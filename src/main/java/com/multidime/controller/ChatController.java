package com.multidime.controller;

import org.apache.tika.Tika;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.multidime.service.HistoryService;
import com.multidime.validation.InputValidator;
import com.multidime.validation.UnreadableFileException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;

@Tag(name = "Chat and resumes", description = "Ask the AI, ask your PDFs, review a resume, match it to a job")
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatClient chatClient;
    private final QuestionAnswerAdvisor questionAnswerAdvisor;
    private final HistoryService history;
    private final Tika tika = new Tika();

    public ChatController(ChatClient chatClient,
                          QuestionAnswerAdvisor questionAnswerAdvisor,
                          HistoryService history) {
        this.chatClient = chatClient;
        this.questionAnswerAdvisor = questionAnswerAdvisor;
        this.history = history;
    }

    /**
     * Pure AI Chat (no PDFs / no RAG)
     */
    @Operation(summary = "Ask the AI",
            description = "A plain question to Gemini. Nothing from the PDFs is used. Returns {\"response\": \"...\"}.")
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest body) {
        String message = body == null ? null : body.message();

        String problem = InputValidator.checkMessage(message);
        if (problem != null) {
            return badRequest(problem);
        }

        try {
            String reply = chatClient.prompt()
                    .user(message)
                    .call()
                    .content();

            history.saveChat("chat", message, reply);
            return ResponseEntity.ok(Map.of("response", reply));

        } catch (Exception e) {
            return internalError("Error in /chat: " + e.getMessage());
        }
    }

    /**
     * AI answer for direct GET
     */
    @Operation(summary = "Ask the AI (GET version)",
            description = "The same as POST /api/chat, but the question is in the URL, so it can be tried in a browser.")
    @GetMapping("/ask")
    public ResponseEntity<?> ask(
            @Parameter(description = "The question to ask (at most 4000 characters)", example = "What is Spring Boot?")
            @RequestParam("question") String question) {
        String problem = InputValidator.checkQuestion(question);
        if (problem != null) {
            return badRequest(problem);
        }

        try {
            String reply = chatClient.prompt()
                    .user(question)
                    .call()
                    .content();

            history.saveChat("ask", question, reply);
            return ResponseEntity.ok(Map.of("response", reply));

        } catch (Exception e) {
            return internalError("Error in /ask: " + e.getMessage());
        }
    }

    /**
     * RAG Chat using Vector Store + Advisor
     */
    @Operation(summary = "Ask your PDFs",
            description = "Finds the most relevant passages in the indexed PDFs and asks Gemini to answer only from them. "
                    + "If the answer is not in the PDFs it says so. Returns {\"response\": \"...\"}.")
    @PostMapping("/rag/chat")
    public ResponseEntity<?> ragChat(@RequestBody ChatRequest body) {
        String message = body == null ? null : body.message();

        String problem = InputValidator.checkMessage(message);
        if (problem != null) {
            return badRequest(problem);
        }

        try {
            String reply = chatClient.prompt()
                    .advisors(questionAnswerAdvisor)
                    .system("""
                            Answer strictly from the PDF context.
                            If the information does not exist in the documents,
                            reply with: "I don't know based on the available documents."
                            """)
                    .user(message)
                    .call()
                    .content();

            history.saveChat("rag", message, reply);
            return ResponseEntity.ok(Map.of("response", reply));

        } catch (Exception e) {
            return internalError("Error in /rag/chat: " + e.getMessage());
        }
    }

    /**
     * Basic Health Endpoint
     */
    @Operation(summary = "Health check", description = "Returns {\"status\": \"UP\"} when the server is running. Uses no AI quota.")
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "AI Chatbot"));
    }

    /**
     * Resume Analyzer Endpoint
     */
    @Operation(summary = "Review a resume",
            description = "Upload a PDF, Word (.doc, .docx), .txt or .rtf file of at most 5 MB. "
                    + "Returns {\"analysis\": \"...\"} where the text is JSON with skills, a rating out of 10 and improvements.")
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeResume(
            @Parameter(description = "The resume file") @RequestParam("file") MultipartFile file) {
        String problem = InputValidator.checkFile(file);
        if (problem != null) {
            return badRequest(problem);
        }

        try {
            String resumeText = extractText(file);

            String prompt = """
                    Analyze the following resume:

                    %s

                    Return strict JSON with:
                    - skills: extracted skills list
                    - rating: score from 1–10
                    - improvements: list of 3 suggestions
                    """.formatted(resumeText);

            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            history.saveResume("ANALYZE", file.getOriginalFilename(), null, result);
            return ResponseEntity.ok(Map.of("analysis", result));

        } catch (UnreadableFileException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return internalError("Error in /analyze: " + e.getMessage());
        }
    }

    /**
     * ATS Score Analyzer
     */
    @Operation(summary = "Match a resume to a job description",
            description = "Upload a resume (same rules as /api/analyze) and a job description of at most 10000 characters. "
                    + "Returns {\"atsReport\": \"...\"} where the text is JSON with a score out of 100, "
                    + "matched keywords, missing keywords and a summary.")
    @PostMapping("/ats-check")
    public ResponseEntity<?> atsCheck(
            @Parameter(description = "The resume file") @RequestParam("file") MultipartFile file,
            @Parameter(description = "The job description text", example = "Looking for a Java backend developer with Spring Boot and SQL experience.")
            @RequestParam("jd") String jobDescription) {

        String problem = InputValidator.checkFile(file);
        if (problem == null) {
            problem = InputValidator.checkJobDescription(jobDescription);
        }
        if (problem != null) {
            return badRequest(problem);
        }

        try {
            String resumeText = extractText(file);

            String prompt = """
                    You are an ATS expert. Compare the resume with the job description.

                    Resume:
                    %s

                    Job Description:
                    %s

                    Return STRICT JSON with:
                    - atsScore (0-100)
                    - matchedKeywords (list)
                    - missingKeywords (list)
                    - summary (short paragraph)
                    """.formatted(resumeText, jobDescription);

            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            history.saveResume("ATS", file.getOriginalFilename(), jobDescription, result);
            return ResponseEntity.ok(Map.of("atsReport", result));

        } catch (UnreadableFileException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return internalError("Error in /ats-check: " + e.getMessage());
        }
    }

    /* --------------------
       Utility Methods
    --------------------- */

    /**
     * Reads the text out of the uploaded file.
     *
     * @throws UnreadableFileException if the file is damaged, protected, or has no text in it
     *                                 (for example a scanned image saved as a PDF)
     */
    private String extractText(MultipartFile file) {
        String text;
        try {
            text = tika.parseToString(file.getInputStream());
        } catch (Exception e) {
            throw new UnreadableFileException("Could not read this file. It may be damaged or password-protected.");
        }
        if (text == null || text.isBlank()) {
            throw new UnreadableFileException(
                    "No text could be read from this file. If it is a scanned image, upload a version with selectable text.");
        }
        return text;
    }

    private ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", message));
    }

    private ResponseEntity<?> internalError(String message) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", message));
    }
}

package com.multidime.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.multidime.service.HistoryService;
import com.multidime.validation.InputValidator;

/**
 * Unit tests for ChatController.
 *
 * The ChatController, ChatClient, QuestionAnswerAdvisor and ApiExceptionHandler are real objects.
 * Only the outside dependencies are replaced with mocks: the ChatModel (Gemini),
 * the VectorStore and the HistoryService (database). So these tests never call Gemini,
 * never touch MySQL and use no API quota.
 */
class ChatControllerTest {

    private ChatModel chatModel;
    private VectorStore vectorStore;
    private HistoryService history;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        vectorStore = mock(VectorStore.class);
        history = mock(HistoryService.class);

        ChatClient chatClient = ChatClient.builder(chatModel).build();
        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(vectorStore).build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ChatController(chatClient, advisor, history))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        // Default behaviour: the "model" answers with a fixed sentence.
        when(chatModel.call(any(Prompt.class))).thenReturn(modelReply("Hello from Gemini"));
    }

    // ---------- helpers ----------

    private static ChatResponse modelReply(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    /** Everything the controller sent to the model, as one string. */
    private String promptSentToModel() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        return String.valueOf(captor.getValue());
    }

    private static MockMultipartFile resume(String text) {
        return new MockMultipartFile("file", "resume.txt", "text/plain", text.getBytes(StandardCharsets.UTF_8));
    }

    private static String chatBody(String message) {
        return "{\"message\":\"" + message + "\"}";
    }

    private void assertNothingWasCalled() {
        verify(chatModel, never()).call(any(Prompt.class));
        verifyNoInteractions(history);
    }

    // ---------- /api/health ----------

    @Test
    @DisplayName("GET /api/health reports the service is up")
    void healthReturnsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("AI Chatbot"));
    }

    // ---------- /api/chat ----------

    @Test
    @DisplayName("POST /api/chat returns the model's reply and saves it to history")
    void chatReturnsModelReply() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody("What is a JVM?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Hello from Gemini"));

        assertThat(promptSentToModel()).contains("What is a JVM?");
        verify(history).saveChat("chat", "What is a JVM?", "Hello from Gemini");
    }

    @Test
    @DisplayName("POST /api/chat rejects an empty message with 400")
    void chatRejectsEmptyMessage() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Message cannot be empty"));

        assertNothingWasCalled();
    }

    @Test
    @DisplayName("POST /api/chat rejects a whitespace-only message with 400")
    void chatRejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Message cannot be empty"));

        assertNothingWasCalled();
    }

    @Test
    @DisplayName("POST /api/chat rejects a body with no message field with 400")
    void chatRejectsMissingMessageField() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Message cannot be empty"));

        assertNothingWasCalled();
    }

    @Test
    @DisplayName("POST /api/chat rejects a message over the length limit with 400")
    void chatRejectsTooLongMessage() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody("a".repeat(InputValidator.MAX_MESSAGE_CHARS + 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("too long")));

        assertNothingWasCalled();
    }

    @Test
    @DisplayName("POST /api/chat rejects a body that is not valid JSON with 400")
    void chatRejectsInvalidJson() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("not valid JSON")));

        assertNothingWasCalled();
    }

    @Test
    @DisplayName("POST /api/chat returns 500 with the error text when the model fails, and saves nothing")
    void chatReturns500WhenModelFails() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Gemini is down"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody("Hello")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value(containsString("Gemini is down")));

        verify(history, never()).saveChat(anyString(), anyString(), anyString());
    }

    // ---------- /api/ask ----------

    @Test
    @DisplayName("GET /api/ask returns the model's reply and saves it to history")
    void askReturnsModelReply() throws Exception {
        mockMvc.perform(get("/api/ask").param("question", "What is Spring Boot?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Hello from Gemini"));

        assertThat(promptSentToModel()).contains("What is Spring Boot?");
        verify(history).saveChat("ask", "What is Spring Boot?", "Hello from Gemini");
    }

    @Test
    @DisplayName("GET /api/ask rejects a blank question with 400")
    void askRejectsBlankQuestion() throws Exception {
        mockMvc.perform(get("/api/ask").param("question", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Question cannot be empty"));

        assertNothingWasCalled();
    }

    @Test
    @DisplayName("GET /api/ask rejects a question over the length limit with 400")
    void askRejectsTooLongQuestion() throws Exception {
        mockMvc.perform(get("/api/ask").param("question", "a".repeat(InputValidator.MAX_MESSAGE_CHARS + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("too long")));

        assertNothingWasCalled();
    }

    @Test
    @DisplayName("GET /api/ask without the question parameter returns 400 and names the parameter")
    void askRejectsMissingParameter() throws Exception {
        mockMvc.perform(get("/api/ask"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("'question'")));
    }

    // ---------- /api/rag/chat ----------

    @Test
    @DisplayName("POST /api/rag/chat rejects an empty message with 400")
    void ragRejectsEmptyMessage() throws Exception {
        mockMvc.perform(post("/api/rag/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Message cannot be empty"));

        assertNothingWasCalled();
    }

    @Test
    @DisplayName("POST /api/rag/chat sends the retrieved PDF text and the 'answer only from documents' rule to the model, and saves the answer")
    void ragSendsRetrievedContextToModel() throws Exception {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("JDK is the Java Development Kit used to build programs.")));

        mockMvc.perform(post("/api/rag/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody("What is the JDK?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Hello from Gemini"));

        assertThat(promptSentToModel())
                .contains("JDK is the Java Development Kit used to build programs.")
                .contains("I don't know based on the available documents");
        verify(history).saveChat("rag", "What is the JDK?", "Hello from Gemini");
    }

    // ---------- /api/analyze ----------

    @Test
    @DisplayName("POST /api/analyze reads the uploaded resume, returns the analysis and saves it to history")
    void analyzeReadsResumeAndReturnsAnalysis() throws Exception {
        mockMvc.perform(multipart("/api/analyze").file(resume("Java Spring Boot developer with MySQL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis").value("Hello from Gemini"));

        assertThat(promptSentToModel()).contains("Java Spring Boot developer with MySQL");
        verify(history).saveResume("ANALYZE", "resume.txt", null, "Hello from Gemini");
    }

    @Test
    @DisplayName("POST /api/analyze without a file returns 400 and says the file is missing")
    void analyzeRejectsMissingFile() throws Exception {
        mockMvc.perform(multipart("/api/analyze"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("'file'")));

        assertNothingWasCalled();
    }

    @Test
    @DisplayName("POST /api/analyze rejects an empty file with 400")
    void analyzeRejectsEmptyFile() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "resume.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/analyze").file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("empty")));

        assertNothingWasCalled();
    }

    @Test
    @DisplayName("POST /api/analyze rejects an unsupported file type with 400")
    void analyzeRejectsUnsupportedFileType() throws Exception {
        MockMultipartFile exe = new MockMultipartFile("file", "resume.exe", "application/octet-stream", "x".getBytes());

        mockMvc.perform(multipart("/api/analyze").file(exe))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Unsupported file type")));

        assertNothingWasCalled();
    }

    @Test
    @DisplayName("POST /api/analyze rejects a file over the size limit with 400")
    void analyzeRejectsTooLargeFile() throws Exception {
        MockMultipartFile big = new MockMultipartFile("file", "resume.pdf", "application/pdf",
                new byte[(int) InputValidator.MAX_FILE_BYTES + 1]);

        mockMvc.perform(multipart("/api/analyze").file(big))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("too large")));

        assertNothingWasCalled();
    }

    @Test
    @DisplayName("POST /api/analyze rejects a file with no readable text with 400 and does not call the model")
    void analyzeRejectsFileWithNoReadableText() throws Exception {
        mockMvc.perform(multipart("/api/analyze").file(resume("   \n   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("No text could be read")));

        assertNothingWasCalled();
    }

    // ---------- /api/ats-check ----------

    @Test
    @DisplayName("POST /api/ats-check sends both the resume and the job description to the model and saves the result")
    void atsCheckSendsResumeAndJobDescription() throws Exception {
        mockMvc.perform(multipart("/api/ats-check")
                        .file(resume("Java Spring Boot developer with MySQL"))
                        .param("jd", "Needs Docker and Kubernetes experience"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atsReport").value("Hello from Gemini"));

        assertThat(promptSentToModel())
                .contains("Java Spring Boot developer with MySQL")
                .contains("Needs Docker and Kubernetes experience");
        verify(history).saveResume("ATS", "resume.txt", "Needs Docker and Kubernetes experience", "Hello from Gemini");
    }

    @Test
    @DisplayName("POST /api/ats-check without a job description returns 400 and names the parameter")
    void atsCheckRejectsMissingJobDescription() throws Exception {
        mockMvc.perform(multipart("/api/ats-check").file(resume("Java developer")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("'jd'")));

        assertNothingWasCalled();
    }

    @Test
    @DisplayName("POST /api/ats-check rejects a blank job description with 400")
    void atsCheckRejectsBlankJobDescription() throws Exception {
        mockMvc.perform(multipart("/api/ats-check")
                        .file(resume("Java developer"))
                        .param("jd", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Job description cannot be empty"));

        assertNothingWasCalled();
    }

    @Test
    @DisplayName("POST /api/ats-check rejects a job description over the length limit with 400")
    void atsCheckRejectsTooLongJobDescription() throws Exception {
        mockMvc.perform(multipart("/api/ats-check")
                        .file(resume("Java developer"))
                        .param("jd", "a".repeat(InputValidator.MAX_JOB_DESCRIPTION_CHARS + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("too long")));

        assertNothingWasCalled();
    }

    @Test
    @DisplayName("POST /api/ats-check rejects an unsupported file type before looking at the job description")
    void atsCheckRejectsUnsupportedFileType() throws Exception {
        MockMultipartFile exe = new MockMultipartFile("file", "resume.exe", "application/octet-stream", "x".getBytes());

        mockMvc.perform(multipart("/api/ats-check").file(exe).param("jd", "Java developer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Unsupported file type")));

        assertNothingWasCalled();
    }
}

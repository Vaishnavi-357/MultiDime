package com.multidime.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.multidime.model.ChatMessage;
import com.multidime.model.ResumeResult;
import com.multidime.repository.ChatMessageRepository;
import com.multidime.repository.ResumeResultRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Saves and reads the history shown on the History page.
 *
 * Saving never throws: if the database has a problem, the user still gets the AI's answer
 * and the problem is only written to the log.
 */
@Service
public class HistoryService {

    private static final Logger log = LoggerFactory.getLogger(HistoryService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_LIMIT = 100;

    private final ChatMessageRepository chats;
    private final ResumeResultRepository resumes;

    public HistoryService(ChatMessageRepository chats, ResumeResultRepository resumes) {
        this.chats = chats;
        this.resumes = resumes;
    }

    public void saveChat(String endpoint, String question, String answer) {
        try {
            chats.save(new ChatMessage(endpoint, question, answer));
        } catch (Exception e) {
            log.warn("Could not save chat history: {}", e.getMessage());
        }
    }

    /**
     * @param kind "ANALYZE" (resume review) or "ATS" (job match)
     * @param aiText the AI's reply; the score is read from it when it contains JSON
     */
    public void saveResume(String kind, String fileName, String jobDescription, String aiText) {
        try {
            resumes.save(new ResumeResult(kind, fileName, jobDescription, extractScore(kind, aiText), aiText));
        } catch (Exception e) {
            log.warn("Could not save resume history: {}", e.getMessage());
        }
    }

    public List<ChatMessage> recentChats(int limit) {
        return chats.findAll(newestFirst(limit)).getContent();
    }

    public List<ResumeResult> recentResumes(int limit) {
        return resumes.findAll(newestFirst(limit)).getContent();
    }

    private static PageRequest newestFirst(int limit) {
        int size = Math.max(1, Math.min(limit, MAX_LIMIT));
        return PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    /** Reads "atsScore" (for ATS) or "rating" (for ANALYZE) from the JSON inside the AI reply. */
    static Double extractScore(String kind, String text) {
        if (text == null) {
            return null;
        }
        try {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            JsonNode root = MAPPER.readTree(text.substring(start, end + 1));
            JsonNode value = root.get("ATS".equals(kind) ? "atsScore" : "rating");
            if (value == null) {
                return null;
            }
            if (value.isNumber()) {
                return value.asDouble();
            }
            if (value.isTextual()) {
                return Double.parseDouble(value.asText().trim());
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}

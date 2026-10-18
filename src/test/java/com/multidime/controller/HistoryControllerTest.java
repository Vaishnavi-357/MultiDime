package com.multidime.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.multidime.model.ChatMessage;
import com.multidime.model.ResumeResult;
import com.multidime.service.HistoryService;

/**
 * Tests for the /api/history endpoints. HistoryService is a mock, so no database is needed.
 */
class HistoryControllerTest {

    private HistoryService history;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        history = mock(HistoryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new HistoryController(history)).build();
    }

    @Test
    @DisplayName("GET /api/history/chats returns the saved questions and answers")
    void chatsReturnsSavedMessages() throws Exception {
        when(history.recentChats(20))
                .thenReturn(List.of(new ChatMessage("rag", "What is a JVM?", "A virtual machine")));

        mockMvc.perform(get("/api/history/chats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].endpoint").value("rag"))
                .andExpect(jsonPath("$[0].question").value("What is a JVM?"))
                .andExpect(jsonPath("$[0].answer").value("A virtual machine"));
    }

    @Test
    @DisplayName("GET /api/history/chats passes the limit parameter on")
    void chatsPassesLimit() throws Exception {
        when(history.recentChats(5)).thenReturn(List.of());

        mockMvc.perform(get("/api/history/chats").param("limit", "5"))
                .andExpect(status().isOk());

        verify(history).recentChats(5);
    }

    @Test
    @DisplayName("GET /api/history/chats returns an empty list when nothing is saved")
    void chatsReturnsEmptyList() throws Exception {
        when(history.recentChats(20)).thenReturn(List.of());

        mockMvc.perform(get("/api/history/chats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("GET /api/history/resumes returns the saved resume results with their scores")
    void resumesReturnsSavedResults() throws Exception {
        when(history.recentResumes(20))
                .thenReturn(List.of(new ResumeResult("ATS", "cv.pdf", "Java developer", 85.0, "{\"atsScore\":85}")));

        mockMvc.perform(get("/api/history/resumes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("ATS"))
                .andExpect(jsonPath("$[0].fileName").value("cv.pdf"))
                .andExpect(jsonPath("$[0].jobDescription").value("Java developer"))
                .andExpect(jsonPath("$[0].score").value(85.0))
                .andExpect(jsonPath("$[0].resultText").value("{\"atsScore\":85}"));
    }
}

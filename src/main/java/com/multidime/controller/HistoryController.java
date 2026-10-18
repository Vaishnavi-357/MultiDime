package com.multidime.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.multidime.model.ChatMessage;
import com.multidime.model.ResumeResult;
import com.multidime.service.HistoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "History", description = "Everything that was asked or analysed, read back from MySQL")
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryService history;

    public HistoryController(HistoryService history) {
        this.history = history;
    }

    @Operation(summary = "Saved questions and answers",
            description = "Newest first. Covers /api/chat, /api/ask and /api/rag/chat.")
    @GetMapping("/chats")
    public List<ChatMessage> chats(
            @Parameter(description = "How many to return (1 to 100)", example = "20")
            @RequestParam(defaultValue = "20") int limit) {
        return history.recentChats(limit);
    }

    @Operation(summary = "Saved resume reviews and job matches",
            description = "Newest first. Covers /api/analyze and /api/ats-check, including the score when the AI gave one.")
    @GetMapping("/resumes")
    public List<ResumeResult> resumes(
            @Parameter(description = "How many to return (1 to 100)", example = "20")
            @RequestParam(defaultValue = "20") int limit) {
        return history.recentResumes(limit);
    }
}

package com.multidime.controller;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The JSON body for /api/chat and /api/rag/chat: {"message": "..."}.
 */
@Schema(description = "A question for the AI")
public record ChatRequest(
        @Schema(description = "The question to ask (at most 4000 characters)",
                example = "What is the difference between JDK, JVM and JRE?",
                maxLength = 4000)
        String message) {
}

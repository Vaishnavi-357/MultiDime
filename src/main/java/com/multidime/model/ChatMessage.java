package com.multidime.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * One question and the answer the AI gave. Saved for /api/chat, /api/ask and /api/rag/chat.
 */
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "chat", "ask" or "rag" */
    @Column(nullable = false, length = 20)
    private String endpoint;

    @Lob
    @Column(nullable = false)
    private String question;

    @Lob
    @Column(nullable = false)
    private String answer;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ChatMessage() {
        // required by JPA
    }

    public ChatMessage(String endpoint, String question, String answer) {
        this.endpoint = endpoint;
        this.question = question;
        this.answer = answer;
    }

    public Long getId() {
        return id;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

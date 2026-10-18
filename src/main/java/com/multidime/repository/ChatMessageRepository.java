package com.multidime.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.multidime.model.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
}

package com.example.im.repo;

import com.example.im.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepo extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdOrderByIdAsc(Long sessionId);
}
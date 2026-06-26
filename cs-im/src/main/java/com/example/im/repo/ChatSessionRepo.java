package com.example.im.repo;

import com.example.im.domain.ChatSession;
import com.example.im.domain.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepo extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findFirstByCustomerIdAndStatusInOrderByIdDesc(String customerId, List<SessionStatus> statuses);
    List<ChatSession> findByStatusOrderByQueuedAtAsc(SessionStatus status);
    List<ChatSession> findByCustomerIdOrderByIdDesc(String customerId);
}
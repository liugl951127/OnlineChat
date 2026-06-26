package com.example.im.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "chat_session", indexes = {
        @Index(name = "idx_cs_customer", columnList = "customerId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 64)
    private String customerId;
    @Column(length = 64)
    private String agentUsername;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SessionStatus status;
    @Column
    private Instant queuedAt;
    @Column
    private Instant acceptedAt;
    @Column
    private Instant lastActiveAt;
    @Column
    private Instant endedAt;
    @Column(length = 16)
    private String endedBy;
    @Column(nullable = false)
    private Instant createdAt;
    @PrePersist void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = SessionStatus.ROBOT;
    }
}
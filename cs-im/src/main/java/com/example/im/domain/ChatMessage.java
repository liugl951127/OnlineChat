package com.example.im.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "chat_message", indexes = {
        @Index(name = "idx_msg_session", columnList = "sessionId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long sessionId;
    /** CUSTOMER / AGENT / ROBOT / SYSTEM */
    @Column(nullable = false, length = 16)
    private String fromRole;
    @Column(nullable = false, length = 64)
    private String fromUser;
    @Column(nullable = false, length = 16)
    private String type;          // TEXT / RICH / SYSTEM
    @Lob
    @Column(nullable = false)
    private String content;
    @Column(columnDefinition = "TEXT")
    private String payloadJson;
    @Column(nullable = false)
    private Instant createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
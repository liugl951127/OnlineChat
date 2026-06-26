package com.example.im.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 审计日志（合规：所有敏感操作落库）
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_al_action", columnList = "action"),
        @Index(name = "idx_al_user", columnList = "userId"),
        @Index(name = "idx_al_time", columnList = "createdAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 32)
    private String action;
    @Column(length = 16)
    private String targetType;
    @Column(length = 64)
    private String targetId;
    @Column(length = 64)
    private String userId;
    @Column(length = 16)
    private String userRole;
    @Lob
    private String detail;
    @Column(length = 64)
    private String traceId;
    @Column(length = 32)
    private String ip;
    @Column(nullable = false)
    private Instant createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
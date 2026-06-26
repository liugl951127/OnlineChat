package com.example.im.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 消息反应（点赞/点踩/表情）
 * 同一用户对同一条消息同一种 emoji 只记一条（unique 约束）
 */
@Entity
@Table(name = "message_reaction", indexes = {
        @Index(name = "idx_mr_msg", columnList = "messageId")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_mr_user_msg_emoji", columnNames = {"messageId", "userId", "emoji"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MessageReaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long messageId;
    @Column(nullable = false, length = 64)
    private String userId;
    @Column(nullable = false, length = 16)
    private String userRole;     // CUSTOMER / AGENT
    @Column(nullable = false, length = 16)
    private String emoji;        // 👍 / 👎 / ❤️ / 😂 / 😮 / 🎉
    @Column(nullable = false)
    private Instant createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
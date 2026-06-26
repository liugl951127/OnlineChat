package com.example.im.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "chat_message", indexes = {
        @Index(name = "idx_msg_session", columnList = "sessionId"),
        @Index(name = "idx_msg_recalled", columnList = "recalled")
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
    private String type;          // TEXT / SYSTEM / RICH / FILE / RECALL
    @Lob
    @Column(nullable = false)
    private String content;
    @Column(columnDefinition = "TEXT")
    private String payloadJson;
    @Column(nullable = false)
    private Instant createdAt;

    /** 引用哪条消息（回复场景） */
    @Column
    private Long replyToId;
    /** 撤回标记（1=已撤回） */
    @Column
    private Integer recalled;
    /** 撤回时间 */
    @Column
    private Instant recalledAt;
    /** 撤回人 */
    @Column(length = 64)
    private String recalledBy;
    /** 消息签名（防伪造） */
    @Column(length = 128)
    private String signature;

    @PrePersist void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (recalled == null) recalled = 0;
    }
}
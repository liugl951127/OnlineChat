package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 聊天会话（MyBatis Plus 实体）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("chat_session")
public class ChatSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String customerId;
    private String agentUsername;
    private SessionStatus status;
    private LocalDateTime queuedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime lastMessageAt;
    private LocalDateTime endedAt;
    private String endedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
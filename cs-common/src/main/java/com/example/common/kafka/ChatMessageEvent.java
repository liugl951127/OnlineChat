package com.example.common.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * 聊天消息事件（Kafka payload）
 *
 * <p>所有消息发送统一走 Kafka，避免服务间直接 RPC 调用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessageEvent implements Serializable {

    /** 事件 ID（UUID） */
    private String eventId;

    /** 消息 ID */
    private String msgId;

    /** 会话 ID */
    private String sessionId;

    /** 发送方 ID */
    private String fromId;

    /** 发送方名称 */
    private String fromName;

    /** 发送方角色：CUSTOMER/AGENT/ROBOT/SYSTEM */
    private String fromRole;

    /** 接收方 ID */
    private String toId;

    /** 接收方角色 */
    private String toRole;

    /** 消息类型：text/image/file/rich */
    private String msgType;

    /** 消息内容 */
    private String content;

    /** 媒体 URL */
    private String mediaUrl;

    /** 客户端 IP */
    private String clientIp;

    /** 服务实例 ID（用于调试） */
    private String sourceInstance;

    /** 事件时间（毫秒） */
    private Long timestamp;

    public Instant eventTime() {
        return timestamp != null ? Instant.ofEpochMilli(timestamp) : Instant.now();
    }
}
package com.example.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 消息统一信封
 */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class WsEnvelope {
    /** 消息类型：CHAT_TEXT / RICH / STATUS_CHANGE / SYSTEM / TYPING / TRANSFER ... */
    private String type;
    private Long sessionId;
    private String fromRole;       // CUSTOMER / AGENT / ROBOT / SYSTEM
    private String fromUser;
    private String content;
    /** 富文本载荷（与 content 二选一）*/
    private Object payload;
    private Long ts;
    private String traceId;
}
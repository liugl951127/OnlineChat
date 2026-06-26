package com.example.common.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 会话事件（开始 / 结束 / 转接 / 强制挂断）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionEvent implements Serializable {

    public enum Action { CREATED, ASSIGNED, TRANSFERRED, ENDED, FORCE_ENDED, TIMEOUT }

    private String eventId;
    private String sessionId;
    private Action action;
    private String customerId;
    private String agentId;
    private String reason;
    private String operatorId;
    private Long timestamp;
}
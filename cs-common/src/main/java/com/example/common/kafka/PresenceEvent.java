package com.example.common.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户在线状态事件
 *
 * <p>WebSocket 连接建立/断开时发送，所有订阅者据此更新在线状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceEvent implements Serializable {

    public enum Action { ONLINE, OFFLINE }

    private String userId;
    private String userRole;
    private Action action;
    private String connectionId;
    private String serviceInstance;
    private Long timestamp;
}
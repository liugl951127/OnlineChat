package com.example.im.domain;

/**
 * 会话状态机 (v2.2.95: 加 WAITING/OPEN/CLOSED 兼容历史 DB 值).
 */
public enum SessionStatus {
    ROBOT,
    QUEUED,
    WAITING,    // 历史值, 同 QUEUED
    IN_SESSION,
    OPEN,       // 历史值, 同 IN_SESSION
    TRADE_CHECKING,
    ENDED,
    CLOSED      // 历史值, 同 ENDED
}
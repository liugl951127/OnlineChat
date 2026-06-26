package com.example.im.domain;

public enum SessionStatus {
    ROBOT,         // 与机器人对话中
    QUEUED,        // 排队
    IN_SESSION,    // 通话中
    TRADE_CHECKING,
    ENDED
}
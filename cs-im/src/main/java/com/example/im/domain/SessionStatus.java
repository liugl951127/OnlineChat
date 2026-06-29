package com.example.im.domain;

/**
 * 会话状态机.
 *
 * <p>与 DB 实际值一致 (v2.2.95):
 * <ul>
 *   <li>ROBOT — 与机器人对话中</li>
 *   <li>QUEUED — 排队等待坐席</li>
 *   <li>WAITING — 同 QUEUED (历史字段, 待迁移)</li>
 *   <li>IN_SESSION / OPEN — 通话中 (历史: OPEN 转 IN_SESSION)</li>
 *   <li>TRADE_CHECKING — 交易合规审核中</li>
 *   <li>ENDED / CLOSED — 已结束 (历史: CLOSED 转 ENDED)</li>
 * </ul>
 *
 * <p>状态机: ROBOT → QUEUED → IN_SESSION → ENDED
 */
public enum SessionStatus {
    ROBOT,
    QUEUED,
    WAITING,    // 同 QUEUED (db 历史值, 兼容)
    IN_SESSION,
    OPEN,       // 同 IN_SESSION (db 历史值, 兼容)
    TRADE_CHECKING,
    ENDED,
    CLOSED      // 同 ENDED (db 历史值, 兼容)
}
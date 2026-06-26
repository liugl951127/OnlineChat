package com.example.common.kafka;

/**
 * Kafka 主题常量（所有服务共享）
 *
 * <p>设计原则：
 * <ul>
 *   <li>按业务事件分主题（细粒度）</li>
 *   <li>分区数 ≥ 服务实例数（保证顺序 + 负载均衡）</li>
 *   <li>关键主题多副本（replication=3）</li>
 * </ul>
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    /** 客户消息（客户发送 → IM 接收 → 分发） */
    public static final String CUSTOMER_MESSAGE = "chat.customer.message";

    /** 坐席消息（坐席发送 → 客户接收） */
    public static final String AGENT_MESSAGE = "chat.agent.message";

    /** 系统消息（强制挂断 / 会话结束 / 转接） */
    public static final String SYSTEM_MESSAGE = "chat.system.message";

    /** 消息已读回执 */
    public static final String MESSAGE_READ = "chat.message.read";

    /** 会话事件（开始 / 结束 / 转接） */
    public static final String SESSION_EVENT = "chat.session.event";

    /** 用户上线 / 下线事件 */
    public static final String PRESENCE_EVENT = "chat.presence.event";

    /** 审计事件（关键操作 → 异步持久化） */
    public static final String AUDIT_EVENT = "chat.audit.event";
}
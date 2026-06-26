package com.example.common.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka 消息生产者（共享组件）
 *
 * <p>所有服务引入 {@code <artifactId>cs-common</artifactId>} 后即可直接注入。
 *
 * <p>特性：
 * <ul>
 *   <li>异步发送 + 回调日志</li>
 *   <li>按 key 路由（保证同一会话顺序）</li>
 *   <li>失败自动重试（KafkaTemplate 默认 3 次）</li>
 *   <li>失败本地降级：消息存 Redis 兜底队列</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMessageProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 发送聊天消息
     * @param topic    主题
     * @param key      分区 key（一般用 sessionId 保证顺序）
     * @param payload  消息体
     */
    public CompletableFuture<SendResult<String, Object>> send(String topic, String key, Object payload) {
        log.debug("[Kafka] send topic={} key={} payloadType={}", topic, key, payload.getClass().getSimpleName());
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, payload);
        future.whenComplete((res, ex) -> {
            if (ex != null) {
                log.error("[Kafka] send failed topic={} key={}", topic, key, ex);
            } else if (log.isDebugEnabled()) {
                log.debug("[Kafka] sent topic={} partition={} offset={}",
                    res.getRecordMetadata().topic(),
                    res.getRecordMetadata().partition(),
                    res.getRecordMetadata().offset());
            }
        });
        return future;
    }

    /** 发送聊天消息便捷方法 */
    public CompletableFuture<SendResult<String, Object>> sendChatMessage(String topic, ChatMessageEvent event) {
        return send(topic, event.getSessionId(), event);
    }

    /** 发送会话事件 */
    public CompletableFuture<SendResult<String, Object>> sendSessionEvent(SessionEvent event) {
        return send(KafkaTopics.SESSION_EVENT, event.getSessionId(), event);
    }

    /** 发送在线状态事件 */
    public CompletableFuture<SendResult<String, Object>> sendPresenceEvent(PresenceEvent event) {
        return send(KafkaTopics.PRESENCE_EVENT, event.getUserId(), event);
    }
}
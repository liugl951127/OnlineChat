package com.example.message.consumer;

import com.example.common.kafka.*;
import com.example.message.service.JsonUtil;
import com.example.message.service.OfflineMessageStore;
import com.example.message.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 聊天消息消费者（核心）
 *
 * <p>消费 Kafka 消息 → 根据接收方在线状态分支：
 * <ul>
 *   <li><b>在线</b>：直接推送（WebSocket / SSE，由 cs-gateway 订阅此事件）</li>
 *   <li><b>离线</b>：存入 Redis 离线队列，上线时拉取</li>
 * </ul>
 *
 * <p>手动 ack：处理成功后才提交 offset，失败重试到 DLQ。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageConsumer {

    private final OfflineMessageStore offlineStore;
    private final PresenceService presenceService;
    private final StringRedisTemplate redis;

    /** 客户消息 */
    @KafkaListener(
        topics = KafkaTopics.CUSTOMER_MESSAGE,
        groupId = "${kafka.consumer.group-id:cs-message-group}",
        containerFactory = "chatMessageListenerFactory"
    )
    public void onCustomerMessage(ChatMessageEvent event, Acknowledgment ack) {
        try {
            dispatch(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Consumer] customer msg {} failed", event.getMsgId(), e);
            // 不 ack → Kafka 重试
            throw e;
        }
    }

    /** 坐席消息 */
    @KafkaListener(
        topics = KafkaTopics.AGENT_MESSAGE,
        groupId = "${kafka.consumer.group-id:cs-message-group}",
        containerFactory = "chatMessageListenerFactory"
    )
    public void onAgentMessage(ChatMessageEvent event, Acknowledgment ack) {
        try {
            dispatch(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Consumer] agent msg {} failed", event.getMsgId(), e);
            throw e;
        }
    }

    /** 系统消息 */
    @KafkaListener(
        topics = KafkaTopics.SYSTEM_MESSAGE,
        groupId = "${kafka.consumer.group-id:cs-message-group}",
        containerFactory = "chatMessageListenerFactory"
    )
    public void onSystemMessage(ChatMessageEvent event, Acknowledgment ack) {
        try {
            // 系统消息不存离线（会话级事件，由前端会话组件处理）
            dispatch(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Consumer] system msg failed", e);
            throw e;
        }
    }

    /** 在线状态事件 */
    @KafkaListener(
        topics = KafkaTopics.PRESENCE_EVENT,
        groupId = "${kafka.consumer.group-id:cs-message-presence}",
        containerFactory = "chatMessageListenerFactory"
    )
    public void onPresenceEvent(PresenceEvent event, Acknowledgment ack) {
        try {
            if (event.getAction() == PresenceEvent.Action.ONLINE) {
                presenceService.online(event.getUserId(), event.getConnectionId(), event.getServiceInstance());
            } else {
                presenceService.offline(event.getConnectionId());
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Consumer] presence event failed", e);
            throw e;
        }
    }

    /**
     * 核心分发逻辑：在线推送 / 离线暂存
     */
    private void dispatch(ChatMessageEvent event) {
        String toId = event.getToId();
        if (toId == null || toId.isEmpty()) {
            log.warn("[Dispatch] no toId, skipping msg={}", event.getMsgId());
            return;
        }

        boolean online = presenceService.isOnline(toId);
        if (online) {
            // 在线 → 通过 Redis Pub/Sub 广播给所有 cs-gateway 实例
            // 网关实例订阅频道：msg:push:{userId}
            // 收到推送后通过 WebSocket 发给该 userId 的所有连接
            String channel = "msg:push:" + toId;
            String payload = JsonUtil.toJson(event);
            Long subs = redis.convertAndSend(channel, payload);
            log.info("[Dispatch] online push to {} via channel={} subs={}", toId, channel, subs);
        } else {
            // 离线 → 存 Redis 离线队列
            offlineStore.push(toId, event);
            log.info("[Dispatch] offline store for user={} msgId={}", toId, event.getMsgId());
        }
    }
}
package com.example.im.consumer;

import com.example.common.WsEnvelope;
import com.example.common.kafka.ChatMessageEvent;
import com.example.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * IM 服务的 Kafka 消费者
 *
 * <p>消费来自 cs-im 自己生产的消息（异步推送）：
 * <ul>
 *   <li>{@code AGENT_MESSAGE} → 推送给客户（@/topic/customer/{customerId}）</li>
 *   <li>{@code CUSTOMER_MESSAGE} → 推送给坐席（@/topic/agent/{agentUsername}）</li>
 * </ul>
 *
 * <p>这样业务分离：IM 只管生产 + 推送，离线消息由 cs-message 兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImMessageConsumer {

    private final SimpMessagingTemplate broker;

    @KafkaListener(
        topics = KafkaTopics.AGENT_MESSAGE,
        groupId = "${spring.kafka.consumer.group-id:cs-im-group}",
        containerFactory = "chatMessageListenerFactory"
    )
    public void onAgentMessage(ChatMessageEvent event, Acknowledgment ack) {
        try {
            WsEnvelope env = new WsEnvelope("MESSAGE", event.getSessionId(),
                event.getFromRole(), event.getFromId(),
                event.getContent(), event, event.getTimestamp(), null);
            broker.convertAndSend("/topic/customer/" + event.getToId(), env);
            log.debug("[IM-Kafka] agent msg {} pushed to customer {}", event.getMsgId(), event.getToId());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[IM-Kafka] agent msg consume failed", e);
            throw e;
        }
    }

    @KafkaListener(
        topics = KafkaTopics.CUSTOMER_MESSAGE,
        groupId = "${spring.kafka.consumer.group-id:cs-im-group}",
        containerFactory = "chatMessageListenerFactory"
    )
    public void onCustomerMessage(ChatMessageEvent event, Acknowledgment ack) {
        try {
            WsEnvelope env = new WsEnvelope("MESSAGE", event.getSessionId(),
                event.getFromRole(), event.getFromId(),
                event.getContent(), event, event.getTimestamp(), null);
            if (event.getToId() != null) {
                broker.convertAndSend("/topic/agent/" + event.getToId(), env);
            }
            log.debug("[IM-Kafka] customer msg {} pushed to agent {}", event.getMsgId(), event.getToId());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[IM-Kafka] customer msg consume failed", e);
            throw e;
        }
    }

    @KafkaListener(
        topics = KafkaTopics.SYSTEM_MESSAGE,
        groupId = "${spring.kafka.consumer.group-id:cs-im-group}",
        containerFactory = "chatMessageListenerFactory"
    )
    public void onSystemMessage(ChatMessageEvent event, Acknowledgment ack) {
        try {
            WsEnvelope env = new WsEnvelope("SYSTEM", event.getSessionId(),
                "SYSTEM", "system",
                event.getContent(), event, event.getTimestamp(), null);
            if (event.getToId() != null) {
                broker.convertAndSend("/topic/customer/" + event.getToId(), env);
                broker.convertAndSend("/topic/agent/" + event.getToId(), env);
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[IM-Kafka] system msg consume failed", e);
            throw e;
        }
    }
}
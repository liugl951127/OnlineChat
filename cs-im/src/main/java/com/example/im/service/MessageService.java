package com.example.im.service;

import com.example.common.ApiException;
import com.example.common.MessageSignature;
import com.example.common.RateLimiter;
import com.example.common.Sanitizer;
import com.example.common.SecurityContextHolder;
import com.example.common.WsEnvelope;
import com.example.common.kafka.ChatMessageEvent;
import com.example.common.kafka.KafkaMessageProducer;
import com.example.common.kafka.KafkaTopics;
import com.example.im.domain.ChatMessage;
import com.example.im.domain.ChatSession;
import com.example.im.repo.ChatMessageRepo;
import com.example.im.repo.ChatSessionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 消息服务（v1.8.0 简化版）：发送（含 XSS 净化 + 限流 + 签名）、撤回
 * Kafka 异步 + WebSocket 推送（Kafka 失败降级 WebSocket）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final ChatMessageRepo messageRepo;
    private final ChatSessionRepo sessionRepo;
    private final AuditService auditService;
    private final KafkaMessageProducer kafkaProducer;

    @Qualifier("messageRateLimiter")
    private final RateLimiter messageRateLimiter;
    @Qualifier("recallRateLimiter")
    private final RateLimiter recallRateLimiter;

    /** 发送消息 */
    @Transactional
    public ChatMessage send(Long sessionId, String fromId, String fromName, String fromRole, String text, String type) {
        // 限流
        if (messageRateLimiter != null && !messageRateLimiter.tryAcquire(fromId)) {
            throw new ApiException(429, "消息发送过于频繁");
        }
        // 会话校验
        ChatSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ApiException(404, "会话不存在"));
        // XSS 净化
        String cleanText = Sanitizer.text(text, 4000);
        if (cleanText.isBlank()) {
            throw new ApiException(400, "消息内容无效");
        }
        // 签名
        String sig = MessageSignature.sign(cleanText, sessionId, fromId, cleanText, System.currentTimeMillis());
        // 入库
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setFromUser(fromId);
        msg.setFromRole(fromRole);
        msg.setType(type != null ? type : "TEXT");
        msg.setContent(cleanText);
        msg.setSignature(sig);
        msg.setCreatedAt(LocalDateTime.now());
        messageRepo.insert(msg);
        // 更新会话最后消息时间
        session.setLastMessageAt(LocalDateTime.now());
        sessionRepo.updateById(session);
        // 推送
        WsEnvelope env = WsEnvelope.builder()
                .type("MESSAGE_NEW")
                .sessionId(sessionId)
                .payload(Map.of("id", msg.getId(), "fromId", fromId, "fromName", fromName,
                        "fromRole", fromRole, "type", msg.getType(), "text", cleanText,
                        "signature", sig, "createdAt", msg.getCreatedAt().toString()))
                .build();
        kafkaProducer.send(KafkaTopics.CUSTOMER_MESSAGE, String.valueOf(sessionId), env);
        auditService.log("MESSAGE_SEND", "MESSAGE", String.valueOf(msg.getId()), fromId, fromRole, "session=" + sessionId);
        return msg;
    }

    /** 撤回消息 */
    @Transactional
    public void recall(Long messageId, String operatorId) {
        if (recallRateLimiter != null && !recallRateLimiter.tryAcquire(operatorId)) {
            throw new ApiException(429, "撤回过于频繁");
        }
        ChatMessage m = messageRepo.findById(messageId)
                .orElseThrow(() -> new ApiException(404, "消息不存在"));
        // 只能撤回自己的消息（或坐席撤回客户的）
        if (!m.getFromUser().equals(operatorId) && !SecurityContextHolder.isAgent()) {
            throw new ApiException(403, "无权撤回他人消息");
        }
        // 2 分钟内可撤回
        if (Duration.between(m.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant(), Instant.now()).toMinutes() > 2) {
            throw new ApiException(400, "超过 2 分钟不能撤回");
        }
        m.setContent("[已撤回]");
        m.setRecalled(1);
        m.setRecalledAt(LocalDateTime.now());
        messageRepo.updateById(m);
        // 推送撤回事件
        WsEnvelope env = WsEnvelope.builder()
                .type("MESSAGE_RECALL")
                .sessionId(m.getSessionId())
                .payload(Map.of("id", messageId))
                .build();
        kafkaProducer.send(KafkaTopics.CUSTOMER_MESSAGE, String.valueOf(m.getSessionId()), env);
        auditService.log("MESSAGE_RECALL", "MESSAGE", String.valueOf(messageId), operatorId, "", "");
    }

    /** 查询历史消息 */
    public List<ChatMessage> history(Long sessionId, int limit) {
        List<ChatMessage> all = messageRepo.findBySessionIdOrderByIdAsc(sessionId);
        if (all.size() <= limit) return all;
        return all.subList(Math.max(0, all.size() - limit), all.size());
    }
}
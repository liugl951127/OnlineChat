package com.example.im.service;

import com.example.common.ApiException;
import com.example.common.MessageSignature;
import com.example.common.RateLimiter;
import com.example.common.Sanitizer;
import com.example.common.SecurityContextHolder;
import com.example.common.WsEnvelope;
import com.example.common.kafka.KafkaMessageProducer;
import com.example.common.kafka.KafkaTopics;
import com.example.common.msg.OfflineMessageStore;
import com.example.common.msg.WsPushService;
import com.example.im.domain.ChatMessage;
import com.example.im.domain.ChatSession;
import com.example.im.repo.ChatMessageRepo;
import com.example.im.repo.ChatSessionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * v2.3.0 消息业务 - 双重防线不丢不堆积
 *
 * <p>双重通道:
 * <ol>
 *   <li>Kafka 异步推送 (按 sessionId 分区保证顺序)</li>
 *   <li>WsPushService 在线 Pub/Sub 即时推送</li>
 *   <li>不在线时 Redis List 兜底, 客户进站立即 drain</li>
 * </ol>
 *
 * <p>消息流:
 * <pre>
 *   send() → 入库 → Kafka (异步, 不阻塞 HTTP) → WsPushService 推送
 *                                                 ↓ 失败
 *                                              落离线
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final ChatMessageRepo messageRepo;
    private final ChatSessionRepo sessionRepo;
    private final AuditService auditService;
    private final KafkaMessageProducer kafkaProducer;
    private final @org.springframework.beans.factory.annotation.Qualifier("commonOfflineMessageStore") OfflineMessageStore offlineStore;
    private final WsPushService wsPushService;

    @Qualifier("messageRateLimiter")
    @Autowired
    private RateLimiter messageRateLimiter;

    @Qualifier("recallRateLimiter")
    @Autowired
    private RateLimiter recallRateLimiter;

    @Autowired
    private AiAssistantService aiAssistantService;

    @Transactional
    public ChatMessage send(Long sessionId, String fromId, String fromName, String fromRole,
                             String text, String type) {
        // 1) 限流
        if (messageRateLimiter != null && !messageRateLimiter.tryAcquire(fromId)) {
            throw new ApiException(429, "消息发送过于频繁，请稍后再试");
        }

        // 2) 会话存在性校验
        ChatSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ApiException(404, "会话不存在"));

        // 3) XSS 净化
        String cleanText = Sanitizer.text(text, 4000);
        if (cleanText.isBlank()) {
            throw new ApiException(400, "消息内容无效");
        }

        // 4) 消息签名
        String sig = MessageSignature.sign(cleanText, sessionId, fromId, cleanText, System.currentTimeMillis());

        // 5) 实体
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setFromUser(fromId);
        msg.setFromRole(fromRole != null ? fromRole : "CUSTOMER");
        msg.setType(type != null ? type : "TEXT");
        msg.setContent(cleanText);
        msg.setSignature(sig);
        msg.setRecalled(0);
        msg.setCreatedAt(LocalDateTime.now());

        // 6) 入库 (持久化第一, 这是兜底)
        messageRepo.insert(msg);

        // 7) 更新会话
        session.setLastMessageAt(LocalDateTime.now());
        sessionRepo.updateById(session);

        // 8) 构造推送 payload (含 id/sig, 客户端可验签)
        String receiverId = resolveReceiverId(session, fromRole);
        String jsonPayload = buildJsonPayload(msg, sessionId, fromId, fromName, fromRole, cleanText, sig);

        // 9) Kafka 异步推送 (持久化 + 跨服务分发, 不阻塞)
        try {
            WsEnvelope env = WsEnvelope.builder()
                    .type("MESSAGE_NEW")
                    .sessionId(sessionId)
                    .fromRole(fromRole)
                    .fromUser(fromId)
                    .content(cleanText)
                    .ts(System.currentTimeMillis())
                    .payload(Map.of(
                            "id", msg.getId(),
                            "fromId", fromId,
                            "fromName", fromName != null ? fromName : fromId,
                            "fromRole", fromRole,
                            "type", msg.getType(),
                            "text", cleanText,
                            "signature", sig,
                            "createdAt", msg.getCreatedAt().toString()
                    ))
                    .build();

            String topic = "AGENT".equals(fromRole) ? KafkaTopics.CUSTOMER_MESSAGE : KafkaTopics.AGENT_MESSAGE;
            kafkaProducer.send(topic, String.valueOf(sessionId), env);
        } catch (Exception e) {
            log.warn("[Kafka] 发送失败（消息已入库, 走 Pub/Sub 兜底）: {}", e.getMessage());
        }

        // 10) Pub/Sub 即时推送 (Redis 跨实例, ms 级)
        try {
            wsPushService.push(receiverId, String.valueOf(msg.getId()), jsonPayload);
        } catch (Exception e) {
            log.warn("[WsPush] Pub/Sub 推送失败, 落离线: {}", e.getMessage());
            offlineStore.push(receiverId, String.valueOf(msg.getId()), jsonPayload);
        }

        // 11) 审计
        auditService.log("MESSAGE_SEND", "MESSAGE", String.valueOf(msg.getId()),
                fromId, fromRole, "session=" + sessionId);

        // 12) v2.1.0 AI 助手
        if ("CUSTOMER".equals(fromRole) && session.getAgentUsername() != null) {
            try {
                aiAssistantService.generateSuggestionAsync(
                    sessionId,
                    session.getCustomerId(),
                    session.getAgentUsername(),
                    msg.getId(),
                    cleanText
                );
            } catch (Exception e) {
                log.warn("[AI] 触发推荐失败（不影响消息）: {}", e.getMessage());
            }
        }

        return msg;
    }

    @Transactional
    public void recall(Long messageId, String operatorId) {
        if (recallRateLimiter != null && !recallRateLimiter.tryAcquire(operatorId)) {
            throw new ApiException(429, "撤回操作过于频繁");
        }

        ChatMessage m = messageRepo.findById(messageId)
                .orElseThrow(() -> new ApiException(404, "消息不存在"));

        boolean isOwner = m.getFromUser().equals(operatorId);
        boolean isAgent = SecurityContextHolder.isAgent();
        if (!isOwner && !isAgent) {
            throw new ApiException(403, "无权撤回他人消息");
        }

        LocalDateTime created = m.getCreatedAt();
        if (created == null || Duration.between(
                created.atZone(java.time.ZoneId.systemDefault()).toInstant(),
                Instant.now()).toMinutes() > 2) {
            throw new ApiException(400, "超过 2 分钟不可撤回");
        }

        m.setContent("[已撤回]");
        m.setRecalled(1);
        m.setRecalledAt(LocalDateTime.now());
        m.setRecalledBy(operatorId);
        messageRepo.updateById(m);

        try {
            WsEnvelope env = WsEnvelope.builder()
                    .type("MESSAGE_RECALL")
                    .sessionId(m.getSessionId())
                    .payload(Map.of("id", messageId))
                    .ts(System.currentTimeMillis())
                    .build();
            String topic = "AGENT".equals(m.getFromRole()) ? KafkaTopics.CUSTOMER_MESSAGE : KafkaTopics.AGENT_MESSAGE;
            kafkaProducer.send(topic, String.valueOf(m.getSessionId()), env);
        } catch (Exception e) {
            log.warn("[Kafka] 撤回推送失败: {}", e.getMessage());
        }

        auditService.log("MESSAGE_RECALL", "MESSAGE", String.valueOf(messageId),
                operatorId, isAgent ? "AGENT" : "CUSTOMER", null);
    }

    public List<ChatMessage> history(Long sessionId, int limit) {
        List<ChatMessage> all = messageRepo.findBySessionIdOrderByIdAsc(sessionId);
        if (all.size() <= limit) return all;
        return all.subList(Math.max(0, all.size() - limit), all.size());
    }

    public List<ChatMessage> replay(Long sessionId, String startTime, String endTime) {
        return messageRepo.findBySessionIdAndTimeRange(sessionId, startTime, endTime);
    }

    private String resolveReceiverId(ChatSession session, String fromRole) {
        if ("AGENT".equals(fromRole)) {
            // 坐席 → 客户
            return session.getCustomerId();
        } else {
            // 客户/机器人 → 坐席
            return session.getAgentUsername() != null ? session.getAgentUsername() : "_queue_";
        }
    }

    private String buildJsonPayload(ChatMessage msg, Long sessionId, String fromId,
                                     String fromName, String fromRole, String cleanText, String sig) {
        // 手动 JSON 序列化, 避免 ObjectMapper 引入额外依赖
        StringBuilder sb = new StringBuilder(512);
        sb.append('{')
          .append("\"type\":\"MESSAGE_NEW\",")
          .append("\"sessionId\":").append(sessionId).append(',')
          .append("\"ts\":").append(System.currentTimeMillis()).append(',')
          .append("\"payload\":{")
          .append("\"id\":").append(msg.getId()).append(',')
          .append("\"fromId\":\"").append(esc(fromId)).append("\",")
          .append("\"fromName\":\"").append(esc(fromName != null ? fromName : fromId)).append("\",")
          .append("\"fromRole\":\"").append(esc(fromRole)).append("\",")
          .append("\"type\":\"").append(esc(msg.getType())).append("\",")
          .append("\"text\":\"").append(esc(cleanText)).append("\",")
          .append("\"signature\":\"").append(esc(sig)).append("\",")
          .append("\"createdAt\":\"").append(msg.getCreatedAt()).append("\"")
          .append("}}");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
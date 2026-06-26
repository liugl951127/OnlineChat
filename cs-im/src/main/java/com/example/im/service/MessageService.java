package com.example.im.service;

import com.example.common.ApiException;                       // 业务异常
import com.example.common.MessageSignature;                 // 消息签名工具
import com.example.common.RateLimiter;                       // 限流器
import com.example.common.Sanitizer;                         // XSS 净化器
import com.example.common.SecurityContextHolder;             // 当前用户上下文
import com.example.common.WsEnvelope;                       // WebSocket 推送信封
import com.example.common.kafka.KafkaMessageProducer;       // Kafka 消息生产者
import com.example.common.kafka.KafkaTopics;                // Kafka 主题常量
import com.example.im.domain.ChatMessage;                   // 消息实体
import com.example.im.domain.ChatSession;                   // 会话实体
import com.example.im.repo.ChatMessageRepo;                 // 消息仓储
import com.example.im.repo.ChatSessionRepo;                 // 会话仓储
import lombok.RequiredArgsConstructor;                      // Lombok 自动注入
import lombok.extern.slf4j.Slf4j;                           // 日志
import org.springframework.beans.factory.annotation.Autowired; // 注入非 final 字段
import org.springframework.beans.factory.annotation.Qualifier; // 按名称注入
import org.springframework.stereotype.Service;               // Spring 业务组件
import org.springframework.transaction.annotation.Transactional; // 事务

import java.time.Duration;                                  // 时间间隔
import java.time.Instant;                                   // UTC 时间戳
import java.time.LocalDateTime;                             // 本地时间
import java.util.*;                                          // 集合类

/**
 * 消息业务服务（v1.9.0 完整版）
 *
 * <p>核心职责：
 * <ul>
 *   <li>发送消息：限流 → XSS 净化 → 签名 → 入库 → Kafka 推送</li>
 *   <li>撤回消息：2 分钟内 + 权限校验</li>
 *   <li>历史查询：分页 / 时间范围</li>
 *   <li>无坐席时的离线消息：Kafka 异步落离线队列</li>
 * </ul>
 *
 * <p>真实业务场景：
 * <ol>
 *   <li>客户发消息时，会话 status=ROBOT → 走机器人</li>
 *   <li>客户请求转人工 → QUEUED 排队 → 坐席接听 IN_SESSION</li>
 *   <li>客户发的消息通过 Kafka 推送给坐席（CS-IM → Kafka → 坐席 poll）</li>
 *   <li>坐席发的消息通过 Kafka 推送给客户</li>
 *   <li>若对方不在线（无坐席），消息入离线队列（Redis List / Kafka 主题）</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    /** 消息仓储 */
    private final ChatMessageRepo messageRepo;

    /** 会话仓储 */
    private final ChatSessionRepo sessionRepo;

    /** 审计服务 */
    private final AuditService auditService;

    /** Kafka 消息生产者 */
    private final KafkaMessageProducer kafkaProducer;

    /** 消息发送限流器（按发送方 ID 限速） */
    @Qualifier("messageRateLimiter")
    @Autowired
    private RateLimiter messageRateLimiter;

    /** 消息撤回限流器 */
    @Qualifier("recallRateLimiter")
    @Autowired
    private RateLimiter recallRateLimiter;

    /** 离线消息存储（用于无坐席 / 客户离线时存消息） */
    @Autowired
    private OfflineMessageStore offlineStore;

    /** AI 助手服务（v2.1.0 客户消息触发实时推荐） */
    @Autowired
    private AiAssistantService aiAssistantService;

    /**
     * 发送消息（HTTP 实时）
     *
     * <p>处理流程：
     * <pre>
     *   1. 限流校验（按发送方）
     *   2. 会话存在性校验
     *   3. XSS 净化（DOMPurify + 长度限制）
     *   4. 消息签名（HMAC-SHA256）
     *   5. 入库（MyBatis Plus insert）
     *   6. 更新会话最后消息时间
     *   7. Kafka 推送给接收方（CUSTOMER_MESSAGE / AGENT_MESSAGE）
     *   8. 若接收方离线，落离线队列
     * </pre>
     *
     * @param sessionId 会话 ID
     * @param fromId    发送方 ID（customerId / agentUsername / "ROBOT"）
     * @param fromName  发送方显示名
     * @param fromRole  发送方角色（CUSTOMER / AGENT / ROBOT / SYSTEM）
     * @param text      消息文本
     * @param type      消息类型（TEXT / IMAGE / FILE / RICH / VIDEO）
     * @return 持久化后的 ChatMessage（含 ID）
     */
    @Transactional
    public ChatMessage send(Long sessionId, String fromId, String fromName, String fromRole,
                             String text, String type) {
        // 1) 限流（同一发送方 1 分钟内最多 60 条）
        if (messageRateLimiter != null && !messageRateLimiter.tryAcquire(fromId)) {
            throw new ApiException(429, "消息发送过于频繁，请稍后再试");
        }

        // 2) 会话存在性校验
        ChatSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ApiException(404, "会话不存在"));

        // 3) XSS 净化（保留纯文本 + 必要 HTML，去除 script/iframe/onerror）
        String cleanText = Sanitizer.text(text, 4000);
        if (cleanText.isBlank()) {
            throw new ApiException(400, "消息内容无效");
        }

        // 4) 消息签名（防篡改 / 防重放）
        String sig = MessageSignature.sign(cleanText, sessionId, fromId, cleanText, System.currentTimeMillis());

        // 5) 构造实体
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setFromUser(fromId);
        msg.setFromRole(fromRole != null ? fromRole : "CUSTOMER");
        msg.setType(type != null ? type : "TEXT");
        msg.setContent(cleanText);
        msg.setSignature(sig);
        msg.setRecalled(0);
        msg.setCreatedAt(LocalDateTime.now());

        // 6) 入库
        messageRepo.insert(msg);

        // 7) 更新会话最后消息时间
        session.setLastMessageAt(LocalDateTime.now());
        sessionRepo.updateById(session);

        // 8) Kafka 异步推送（按接收方角色选主题）
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

            // 根据发送方角色选择 Kafka 主题
            String topic;
            if ("AGENT".equals(fromRole)) {
                topic = KafkaTopics.AGENT_MESSAGE; // 坐席→客户
            } else {
                topic = KafkaTopics.CUSTOMER_MESSAGE; // 客户/机器人→坐席
            }
            kafkaProducer.send(topic, String.valueOf(sessionId), env);

            // 9) 若接收方离线（无坐席 / 客户不在线），落离线队列
            boolean receiverOnline = isReceiverOnline(session, fromRole);
            if (!receiverOnline) {
                offlineStore.save(sessionId, fromId, fromRole, cleanText);
                log.info("[Offline] 消息 {} 落离线队列（session={}, from={}）", msg.getId(), sessionId, fromId);
            }
        } catch (Exception e) {
            // Kafka 失败不影响消息发送
            log.warn("[Kafka] 发送失败（消息已入库）: {}", e.getMessage());
        }

        // 10) 审计
        auditService.log("MESSAGE_SEND", "MESSAGE", String.valueOf(msg.getId()),
                fromId, fromRole, "session=" + sessionId);

        // 11) v2.1.0 AI 助手 — 客户发消息 → 异步生成推荐话术推送给坐席
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

    /**
     * 撤回消息
     *
     * <p>规则：
     * <ul>
     *   <li>只能撤回自己的消息（管理员 / 坐席可撤回他人）</li>
     *   <li>仅 2 分钟内可撤回</li>
     * </ul>
     */
    @Transactional
    public void recall(Long messageId, String operatorId) {
        // 1) 限流
        if (recallRateLimiter != null && !recallRateLimiter.tryAcquire(operatorId)) {
            throw new ApiException(429, "撤回操作过于频繁");
        }

        // 2) 查消息
        ChatMessage m = messageRepo.findById(messageId)
                .orElseThrow(() -> new ApiException(404, "消息不存在"));

        // 3) 权限校验：自己的消息 OR 坐席可撤回
        boolean isOwner = m.getFromUser().equals(operatorId);
        boolean isAgent = SecurityContextHolder.isAgent();
        if (!isOwner && !isAgent) {
            throw new ApiException(403, "无权撤回他人消息");
        }

        // 4) 时间窗口（2 分钟）
        LocalDateTime created = m.getCreatedAt();
        if (created == null || Duration.between(
                created.atZone(java.time.ZoneId.systemDefault()).toInstant(),
                Instant.now()).toMinutes() > 2) {
            throw new ApiException(400, "超过 2 分钟不可撤回");
        }

        // 5) 更新为已撤回
        m.setContent("[已撤回]");
        m.setRecalled(1);
        m.setRecalledAt(LocalDateTime.now());
        m.setRecalledBy(operatorId);
        messageRepo.updateById(m);

        // 6) Kafka 推送撤回事件
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

        // 7) 审计
        auditService.log("MESSAGE_RECALL", "MESSAGE", String.valueOf(messageId),
                operatorId, isAgent ? "AGENT" : "CUSTOMER", null);
    }

    /**
     * 历史消息（按会话 ID 升序，取最后 N 条）
     */
    public List<ChatMessage> history(Long sessionId, int limit) {
        List<ChatMessage> all = messageRepo.findBySessionIdOrderByIdAsc(sessionId);
        if (all.size() <= limit) return all;
        return all.subList(Math.max(0, all.size() - limit), all.size());
    }

    /**
     * 视频回溯数据（消息列表 + 时间戳）
     */
    public List<ChatMessage> replay(Long sessionId, String startTime, String endTime) {
        return messageRepo.findBySessionIdAndTimeRange(sessionId, startTime, endTime);
    }

    /**
     * 内部辅助：判断接收方是否在线
     *
     * <p>逻辑：
     * <ul>
     *   <li>发送方=AGENT，接收方=客户 → 检查客户 PresenceService</li>
     *   <li>发送方=CUSTOMER/ROBOT，接收方=坐席 → 检查会话是否 IN_SESSION</li>
     * </ul>
     */
    private boolean isReceiverOnline(ChatSession session, String fromRole) {
        if ("AGENT".equals(fromRole)) {
            // 坐席发的，接收方是客户；客户在线判定留给 cs-presence 服务
            return true; // 简化：暂时认为客户在线
        } else {
            // 客户/机器人发的，接收方是坐席
            return "IN_SESSION".equals(session.getStatus())
                    && session.getAgentUsername() != null;
        }
    }
}
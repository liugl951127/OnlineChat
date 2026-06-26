package com.example.im.service;

import com.example.common.ApiException;
import com.example.common.MessageSignature;
import com.example.common.RateLimiter;
import com.example.common.Sanitizer;
import com.example.common.SecurityContextHolder;
import com.example.common.WsEnvelope;
import com.example.im.domain.ChatMessage;
import com.example.im.domain.ChatSession;
import com.example.im.domain.MessageReaction;
import com.example.im.repo.ChatMessageRepo;
import com.example.im.repo.ChatSessionRepo;
import com.example.im.repo.MessageReactionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 消息服务：发送（含 XSS 净化 + 限流 + 签名）、引用、撤回、反应
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final ChatMessageRepo messageRepo;
    private final ChatSessionRepo sessionRepo;
    private final MessageReactionRepo reactionRepo;
    private final SimpMessagingTemplate broker;
    private final AuditService auditService;

    @Qualifier("messageRateLimiter")
    private final RateLimiter messageRateLimiter;
    @Qualifier("recallRateLimiter")
    private final RateLimiter recallRateLimiter;
    @Qualifier("reactionRateLimiter")
    private final RateLimiter reactionRateLimiter;

    @Value("${cs.message.recall-window-seconds:120}")
    private long recallWindowSeconds;
    @Value("${cs.message.sign-secret:please-change-me-in-production-32-chars-min}")
    private String signSecret;
    @Value("${cs.message.max-length:4000}")
    private int maxMessageLength;

    /** 合法 emoji 白名单（防注入） */
    private static final Set<String> VALID_EMOJI = Set.of(
            "👍", "👎", "❤️", "😂", "😮", "🎉", "🙏", "👏"
    );

    // ==================== 发送消息（含净化 + 限流 + 签名） ====================

    @Transactional
    public ChatMessage send(Long sessionId, String fromRole, String fromUser, String content, Long replyToId) {
        // 1) 限流
        if (!messageRateLimiter.tryAcquire("msg:" + fromUser)) {
            throw new ApiException(429, "发送过于频繁，请稍后再试");
        }

        // 2) 安全净化
        String cleanContent = Sanitizer.text(content, maxMessageLength);
        if (cleanContent == null || cleanContent.isEmpty()) {
            throw new ApiException(400, "消息内容为空");
        }

        // 3) 会话存在性
        ChatSession s = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ApiException(404, "会话不存在"));

        // 4) 权限：客户只能发自己的；坐席只能发自己接听的
        if ("CUSTOMER".equals(fromRole) && !fromUser.equals(s.getCustomerId())) {
            throw new ApiException(403, "无权在该会话发言");
        }
        if ("AGENT".equals(fromRole) && !fromUser.equals(s.getAgentUsername())) {
            throw new ApiException(403, "无权在该会话发言");
        }

        // 5) 引用校验
        if (replyToId != null) {
            ChatMessage replied = messageRepo.findById(replyToId)
                    .orElseThrow(() -> new ApiException(404, "被引用的消息不存在"));
            if (!replied.getSessionId().equals(sessionId)) {
                throw new ApiException(400, "引用消息不属于本会话");
            }
        }

        // 6) 签名
        long ts = System.currentTimeMillis();
        String sig = MessageSignature.sign(signSecret, sessionId, fromUser, cleanContent, ts);

        ChatMessage m = ChatMessage.builder()
                .sessionId(sessionId)
                .fromRole(fromRole)
                .fromUser(fromUser)
                .type("TEXT")
                .content(cleanContent)
                .replyToId(replyToId)
                .recalled(0)
                .signature(sig)
                .build();
        messageRepo.save(m);

        // 7) 广播
        broadcast(s, m);

        // 8) 审计
        auditService.log("MESSAGE_SEND", "MESSAGE", String.valueOf(m.getId()),
                fromUser, fromRole, "len=" + cleanContent.length());
        return m;
    }

    // ==================== 撤回 ====================

    @Transactional
    public ChatMessage recall(Long messageId, String fromUser) {
        if (!recallRateLimiter.tryAcquire("recall:" + fromUser)) {
            throw new ApiException(429, "撤回操作过于频繁");
        }

        ChatMessage m = messageRepo.findById(messageId)
                .orElseThrow(() -> new ApiException(404, "消息不存在"));

        if (m.getRecalled() != null && m.getRecalled() == 1) {
            throw new ApiException(400, "消息已被撤回");
        }

        // 权限校验：只能撤回自己的
        if (!fromUser.equals(m.getFromUser())) {
            throw new ApiException(403, "只能撤回自己的消息");
        }

        // 时间窗口
        if (m.getCreatedAt() != null) {
            long age = Duration.between(m.getCreatedAt(), Instant.now()).getSeconds();
            if (age > recallWindowSeconds) {
                throw new ApiException(400, "超过 " + recallWindowSeconds + " 秒不可撤回");
            }
        }

        m.setRecalled(1);
        m.setRecalledAt(Instant.now());
        m.setRecalledBy(fromUser);
        m.setContent("");  // 清空原文
        messageRepo.save(m);

        ChatSession s = sessionRepo.findById(m.getSessionId()).orElse(null);
        if (s != null) {
            WsEnvelope env = new WsEnvelope("RECALL", s.getId(),
                    m.getFromRole(), fromUser, "消息已撤回",
                    m, System.currentTimeMillis(), null);
            broker.convertAndSend("/topic/customer/" + s.getCustomerId(), env);
            if (s.getAgentUsername() != null) {
                broker.convertAndSend("/topic/agent/" + s.getAgentUsername(), env);
            }
        }

        auditService.log("MESSAGE_RECALL", "MESSAGE", String.valueOf(messageId),
                fromUser, m.getFromRole(), "reason=by-user");
        return m;
    }

    // ==================== 反应（点赞/点踩/表情） ====================

    @Transactional
    public Map<String, Object> react(Long messageId, String fromUser, String fromRole, String emoji) {
        if (!reactionRateLimiter.tryAcquire("react:" + fromUser)) {
            throw new ApiException(429, "操作过于频繁");
        }

        // 白名单
        String cleanEmoji = Sanitizer.emoji(emoji);
        if (cleanEmoji == null || !VALID_EMOJI.contains(cleanEmoji)) {
            throw new ApiException(400, "不支持的 emoji");
        }

        ChatMessage m = messageRepo.findById(messageId)
                .orElseThrow(() -> new ApiException(404, "消息不存在"));

        ChatSession s = sessionRepo.findById(m.getSessionId())
                .orElseThrow(() -> new ApiException(404, "会话不存在"));

        // 权限：必须是会话参与者
        if (!fromUser.equals(s.getCustomerId()) && !fromUser.equals(s.getAgentUsername())) {
            throw new ApiException(403, "只能对会话内的消息反应");
        }

        // 切换（toggle）
        Optional<MessageReaction> existing = reactionRepo.findByMessageIdAndUserIdAndEmoji(messageId, fromUser, cleanEmoji);
        boolean added;
        if (existing.isPresent()) {
            reactionRepo.delete(existing.get());
            added = false;
        } else {
            reactionRepo.save(MessageReaction.builder()
                    .messageId(messageId).userId(fromUser).userRole(fromRole)
                    .emoji(cleanEmoji).build());
            added = true;
        }

        // 重新统计
        Map<String, Long> stats = reactionRepo.findByMessageId(messageId).stream()
                .collect(Collectors.groupingBy(MessageReaction::getEmoji, Collectors.counting()));

        // 广播
        WsEnvelope env = new WsEnvelope("REACTION", s.getId(),
                fromRole, fromUser, added ? "+1" : "-1",
                Map.of("messageId", messageId, "emoji", cleanEmoji, "added", added, "stats", stats),
                System.currentTimeMillis(), null);
        broker.convertAndSend("/topic/customer/" + s.getCustomerId(), env);
        if (s.getAgentUsername() != null) {
            broker.convertAndSend("/topic/agent/" + s.getAgentUsername(), env);
        }

        auditService.log(added ? "REACTION_ADD" : "REACTION_REMOVE", "MESSAGE",
                String.valueOf(messageId), fromUser, fromRole, "emoji=" + cleanEmoji);

        return Map.of("added", added, "emoji", cleanEmoji, "stats", stats);
    }

    /** 批量获取某会话所有消息的反应统计 */
    public Map<Long, Map<String, Long>> reactionsOfMessages(Long sessionId) {
        List<Long> msgIds = messageRepo.findBySessionIdOrderByIdAsc(sessionId).stream()
                .map(ChatMessage::getId).toList();
        if (msgIds.isEmpty()) return Collections.emptyMap();
        return reactionRepo.findByMessageIdIn(msgIds).stream()
                .collect(Collectors.groupingBy(MessageReaction::getMessageId,
                        Collectors.groupingBy(MessageReaction::getEmoji, Collectors.counting())))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // ==================== 内部 ====================

    private void broadcast(ChatSession s, ChatMessage m) {
        WsEnvelope env = new WsEnvelope("CHAT_TEXT", s.getId(),
                m.getFromRole(), m.getFromUser(), m.getContent(),
                m, System.currentTimeMillis(), null);
        broker.convertAndSend("/topic/customer/" + s.getCustomerId(), env);
        if (s.getAgentUsername() != null) {
            broker.convertAndSend("/topic/agent/" + s.getAgentUsername(), env);
        }
    }
}
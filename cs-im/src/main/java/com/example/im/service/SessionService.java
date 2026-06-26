package com.example.im.service;

import com.example.common.ApiException;
import com.example.im.domain.ChatMessage;
import com.example.im.domain.ChatSession;
import com.example.im.domain.SessionStatus;
import com.example.im.repo.ChatMessageMapper;  // unused but for ref
import com.example.im.repo.ChatMessageRepo;
import com.example.im.repo.ChatSessionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IM 会话服务（v1.8.0 简化版）: 会话生命周期
 * 推送走 Kafka，Database 用 MyBatis Plus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final ChatSessionRepo sessionRepo;
    private final ChatMessageRepo messageRepo;
    private final AuditService auditService;

    /** 创建或获取活跃会话（默认 ROBOT 状态） */
    @Transactional
    public ChatSession getOrCreate(String customerId) {
        return sessionRepo.findActiveByCustomer(customerId).orElseGet(() -> {
            ChatSession s = new ChatSession();
            s.setCustomerId(customerId);
            s.setStatus(SessionStatus.ROBOT);
            s.setCreatedAt(LocalDateTime.now());
            s.setLastActiveAt(LocalDateTime.now());
            sessionRepo.insert(s);
            return s;
        });
    }

    /** 客户转人工：ROBOT -> QUEUED */
    @Transactional
    public ChatSession transferToQueue(String customerId) {
        ChatSession s = activeOrThrow(customerId);
        if (s.getStatus() == SessionStatus.QUEUED) return s;
        if (s.getStatus() != SessionStatus.ROBOT) {
            throw new ApiException(400, "当前状态不可转人工: " + s.getStatus());
        }
        s.setStatus(SessionStatus.QUEUED);
        s.setQueuedAt(LocalDateTime.now());
        s.setLastActiveAt(LocalDateTime.now());
        sessionRepo.updateById(s);
        auditService.log("TRANSFER_QUEUE", "SESSION", String.valueOf(s.getId()), customerId, "CUSTOMER", null);
        log.info("[IM] customer={} enter queue, session={}", customerId, s.getId());
        return s;
    }

    /** 坐席接听：QUEUED -> IN_SESSION */
    @Transactional
    public ChatSession acceptByAgent(String agentUsername, Long sessionId) {
        ChatSession s;
        if (sessionId == null) {
            List<ChatSession> queue = sessionRepo.findByStatus(SessionStatus.QUEUED);
            s = queue.isEmpty() ? null : queue.get(0);
            if (s == null) throw new ApiException(404, "无排队客户");
        } else {
            s = sessionRepo.findById(sessionId).orElseThrow(() -> new ApiException(404, "会话不存在"));
        }
        if (s.getStatus() != SessionStatus.QUEUED) {
            throw new ApiException(400, "该会话不在排队: " + s.getStatus());
        }
        s.setAgentUsername(agentUsername);
        s.setStatus(SessionStatus.IN_SESSION);
        s.setAcceptedAt(LocalDateTime.now());
        s.setLastActiveAt(LocalDateTime.now());
        sessionRepo.updateById(s);
        saveSystemMsg(s, "坐席已接入");
        auditService.log("ACCEPT", "SESSION", String.valueOf(s.getId()), agentUsername, "AGENT", null);
        return s;
    }

    /** 挂断 */
    @Transactional
    public ChatSession hangup(String who, String whoId, Long sessionId) {
        ChatSession s = sessionRepo.findById(sessionId).orElseThrow(() -> new ApiException(404, "会话不存在"));
        if (s.getStatus() == SessionStatus.ENDED) return s;
        s.setStatus(SessionStatus.ENDED);
        s.setEndedAt(LocalDateTime.now());
        s.setEndedBy(who);
        s.setLastActiveAt(LocalDateTime.now());
        sessionRepo.updateById(s);
        saveSystemMsg(s, "会话已结束（" + who + " 挂断）");
        auditService.log("HANGUP", "SESSION", String.valueOf(s.getId()), whoId,
                who.equals("CUSTOMER") ? "CUSTOMER" : "AGENT", null);
        return s;
    }

    // ==================== 查询 ====================

    public ChatSession findById(Long id) { return sessionRepo.findById(id).orElse(null); }

    public ChatSession findActive(String customerId) {
        return activeOrThrow(customerId);
    }

    public List<ChatSession> listQueue() {
        return sessionRepo.findByStatus(SessionStatus.QUEUED);
    }

    public List<ChatSession> listByCustomer(String customerId) {
        return sessionRepo.findByCustomerId(customerId);
    }

    public List<ChatSession> listByAgent(String agentUsername) {
        return sessionRepo.findByAgentUsername(agentUsername);
    }

    /** 管理员强制挂断 */
    @Transactional
    public ChatSession forceHangup(Long sessionId, String reason) {
        ChatSession s = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ApiException(404, "会话不存在"));
        if (s.getStatus() == SessionStatus.ENDED) return s;
        s.setStatus(SessionStatus.ENDED);
        s.setEndedAt(LocalDateTime.now());
        s.setEndedBy("ADMIN");
        s.setLastActiveAt(LocalDateTime.now());
        sessionRepo.updateById(s);
        saveSystemMsg(s, "【系统】管理员强制挂断：" + reason);
        log.warn("[Admin] force-hangup session={} reason={}", sessionId, reason);
        return s;
    }

    /** 实时看板统计 */
    public Map<String, Object> stats() {
        Map<String, Object> m = new HashMap<>();
        LocalDateTime startOfToday = LocalDate.now(ZoneId.systemDefault()).atStartOfDay();
        List<ChatSession> all = sessionRepo.findAll();
        m.put("totalSessions", all.size());
        m.put("queued", all.stream().filter(x -> x.getStatus() == SessionStatus.QUEUED).count());
        m.put("inSession", all.stream().filter(x -> x.getStatus() == SessionStatus.IN_SESSION).count());
        m.put("todaySessions", all.stream().filter(x -> x.getCreatedAt() != null && x.getCreatedAt().isAfter(startOfToday)).count());
        m.put("todayEnded", all.stream().filter(x -> x.getStatus() == SessionStatus.ENDED && x.getEndedAt() != null && x.getEndedAt().isAfter(startOfToday)).count());
        return m;
    }

    public List<ChatMessage> messagesOf(Long sessionId) {
        return messageRepo.findBySessionIdOrderByIdAsc(sessionId);
    }

    // ==================== 内部辅助 ====================

    ChatSession activeOrThrow(String customerId) {
        return sessionRepo.findActiveByCustomer(customerId)
                .orElseThrow(() -> new ApiException(404, "无活跃会话"));
    }

    public ChatMessage saveSystemMsg(ChatSession s, String content) {
        ChatMessage m = new ChatMessage();
        m.setSessionId(s.getId());
        m.setFromUser("SYSTEM");
        m.setFromRole("SYSTEM");
        m.setType("SYSTEM");
        m.setContent(content);
        m.setCreatedAt(LocalDateTime.now());
        messageRepo.insert(m);
        s.setLastActiveAt(LocalDateTime.now());
        sessionRepo.updateById(s);
        return m;
    }
}
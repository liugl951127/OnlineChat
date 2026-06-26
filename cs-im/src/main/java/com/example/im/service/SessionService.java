package com.example.im.service;

import com.example.common.ApiException;
import com.example.common.WsEnvelope;
import com.example.im.domain.ChatMessage;
import com.example.im.domain.ChatSession;
import com.example.im.domain.SessionStatus;
import com.example.im.repo.ChatMessageRepo;
import com.example.im.repo.ChatSessionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IM 会话服务：会话生命周期 + 消息存档 + 广播
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final ChatSessionRepo sessionRepo;
    private final ChatMessageRepo messageRepo;
    private final SimpMessagingTemplate broker;
    private final AuditService auditService;

    /** 创建或获取活跃会话（默认 ROBOT 状态） */
    @Transactional
    public ChatSession getOrCreate(String customerId) {
        return sessionRepo.findFirstByCustomerIdAndStatusInOrderByIdDesc(
                customerId, List.of(SessionStatus.ROBOT, SessionStatus.QUEUED,
                        SessionStatus.IN_SESSION, SessionStatus.TRADE_CHECKING))
                .orElseGet(() -> sessionRepo.save(ChatSession.builder()
                        .customerId(customerId)
                        .status(SessionStatus.ROBOT)
                        .lastActiveAt(Instant.now())
                        .build()));
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
        s.setQueuedAt(Instant.now());
        s.setLastActiveAt(Instant.now());
        sessionRepo.save(s);
        broker.convertAndSend("/topic/agents", statusEnv("QUEUE_JOINED", s));
        auditService.log("TRANSFER_QUEUE", "SESSION", String.valueOf(s.getId()), customerId, "CUSTOMER", null);
        log.info("[IM] customer={} enter queue, session={}", customerId, s.getId());
        return s;
    }

    /** 坐席接听：QUEUED -> IN_SESSION */
    @Transactional
    public ChatSession acceptByAgent(String agentUsername, Long sessionId) {
        ChatSession s;
        if (sessionId == null) {
            s = sessionRepo.findByStatusOrderByQueuedAtAsc(SessionStatus.QUEUED).stream().findFirst()
                    .orElseThrow(() -> new ApiException(404, "无排队客户"));
        } else {
            s = sessionRepo.findById(sessionId).orElseThrow(() -> new ApiException(404, "会话不存在"));
        }
        if (s.getStatus() != SessionStatus.QUEUED) {
            throw new ApiException(400, "该会话不在排队: " + s.getStatus());
        }
        s.setAgentUsername(agentUsername);
        s.setStatus(SessionStatus.IN_SESSION);
        s.setAcceptedAt(Instant.now());
        s.setLastActiveAt(Instant.now());
        sessionRepo.save(s);

        broker.convertAndSend("/topic/agent/" + agentUsername, statusEnv("ACCEPTED", s));
        broker.convertAndSend("/topic/customer/" + s.getCustomerId(), statusEnv("AGENT_JOINED", s));
        broker.convertAndSend("/topic/agents", statusEnv("QUEUE_LEAVE", s));
        saveAndBroadcast(s, "SYSTEM", "坐席已接入", "SYSTEM");
        auditService.log("ACCEPT", "SESSION", String.valueOf(s.getId()), agentUsername, "AGENT", null);
        return s;
    }

    /** 挂断 */
    @Transactional
    public ChatSession hangup(String who, String whoId, Long sessionId) {
        ChatSession s = sessionRepo.findById(sessionId).orElseThrow(() -> new ApiException(404, "会话不存在"));
        if (s.getStatus() == SessionStatus.ENDED) return s;
        s.setStatus(SessionStatus.ENDED);
        s.setEndedAt(Instant.now());
        s.setEndedBy(who);
        s.setLastActiveAt(Instant.now());
        sessionRepo.save(s);
        saveAndBroadcast(s, "SYSTEM", "会话已结束（" + who + " 挂断）", "SYSTEM");
        broker.convertAndSend("/topic/customer/" + s.getCustomerId(), statusEnv("ENDED", s));
        if (s.getAgentUsername() != null) {
            broker.convertAndSend("/topic/agent/" + s.getAgentUsername(), statusEnv("ENDED", s));
        }
        auditService.log("HANGUP", "SESSION", String.valueOf(s.getId()), whoId,
                who.equals("CUSTOMER") ? "CUSTOMER" : "AGENT", null);
        return s;
    }

    // ==================== 查询 ====================

    public ChatSession findById(Long id) { return sessionRepo.findById(id).orElse(null); }

    public ChatSession findActive(String customerId) {
        return sessionRepo.findFirstByCustomerIdAndStatusInOrderByIdDesc(
                customerId, List.of(SessionStatus.ROBOT, SessionStatus.QUEUED,
                        SessionStatus.IN_SESSION, SessionStatus.TRADE_CHECKING))
                .orElseThrow(() -> new ApiException(404, "无活跃会话"));
    }

    public List<ChatSession> listQueue() {
        return sessionRepo.findByStatusOrderByQueuedAtAsc(SessionStatus.QUEUED);
    }

    public List<ChatSession> listByCustomer(String customerId) {
        return sessionRepo.findByCustomerIdOrderByIdDesc(customerId);
    }

    public List<ChatSession> listByAgent(String agentUsername) {
        return sessionRepo.findAll().stream()
                .filter(s -> agentUsername.equals(s.getAgentUsername()))
                .toList();
    }

    public Page<ChatSession> pageAll(Pageable pageable) {
        return sessionRepo.findAll(pageable);
    }

    /** 管理员强制挂断 */
    @Transactional
    public ChatSession forceHangup(Long sessionId, String reason) {
        ChatSession s = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ApiException(404, "会话不存在"));
        if (s.getStatus() == SessionStatus.ENDED) return s;
        s.setStatus(SessionStatus.ENDED);
        s.setEndedAt(Instant.now());
        s.setEndedBy("ADMIN");
        s.setLastActiveAt(Instant.now());
        sessionRepo.save(s);
        saveAndBroadcast(s, "SYSTEM", "【系统】管理员强制挂断：" + reason, "ADMIN");
        broker.convertAndSend("/topic/customer/" + s.getCustomerId(), statusEnv("ENDED", s));
        if (s.getAgentUsername() != null) {
            broker.convertAndSend("/topic/agent/" + s.getAgentUsername(), statusEnv("ENDED", s));
        }
        log.warn("[Admin] force-hangup session={} reason={}", sessionId, reason);
        return s;
    }

    /** 实时看板统计 */
    public Map<String, Object> stats() {
        Map<String, Object> m = new HashMap<>();
        Instant startOfToday = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();
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
        return sessionRepo.findFirstByCustomerIdAndStatusInOrderByIdDesc(
                customerId, List.of(SessionStatus.ROBOT, SessionStatus.QUEUED,
                        SessionStatus.IN_SESSION, SessionStatus.TRADE_CHECKING))
                .orElseThrow(() -> new ApiException(404, "无活跃会话"));
    }

    public ChatMessage saveAndBroadcast(ChatSession s, String fromRole, String content, String fromUser) {
        ChatMessage m = ChatMessage.builder()
                .sessionId(s.getId())
                .fromRole(fromRole)
                .fromUser(fromUser)
                .type("TEXT")
                .content(content)
                .build();
        messageRepo.save(m);
        s.setLastActiveAt(Instant.now());
        sessionRepo.save(s);

        WsEnvelope env = new WsEnvelope(
                fromRole.equals("SYSTEM") ? "SYSTEM" : "CHAT_TEXT",
                s.getId(), fromRole, fromUser, content, null, System.currentTimeMillis(), null);
        broker.convertAndSend("/topic/customer/" + s.getCustomerId(), env);
        if (s.getAgentUsername() != null) {
            broker.convertAndSend("/topic/agent/" + s.getAgentUsername(), env);
        }
        return m;
    }

    /** 保存并广播富文本 */
    public ChatMessage saveRich(ChatSession s, String fromRole, com.example.common.RichMessage rm) {
        String json;
        try { json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(rm); }
        catch (Exception e) { json = "{\"text\":\"" + rm.getText() + "\"}"; }
        ChatMessage m = ChatMessage.builder()
                .sessionId(s.getId())
                .fromRole(fromRole)
                .fromUser(fromRole)
                .type("RICH")
                .content(rm.getText() == null ? "" : rm.getText())
                .payloadJson(json)
                .build();
        messageRepo.save(m);
        s.setLastActiveAt(Instant.now());
        sessionRepo.save(s);

        WsEnvelope env = new WsEnvelope("RICH", s.getId(), fromRole, fromRole,
                rm.getText(), rm, System.currentTimeMillis(), null);
        broker.convertAndSend("/topic/customer/" + s.getCustomerId(), env);
        if (s.getAgentUsername() != null) {
            broker.convertAndSend("/topic/agent/" + s.getAgentUsername(), env);
        }
        return m;
    }

    private WsEnvelope statusEnv(String type, ChatSession s) {
        return new WsEnvelope(type, s.getId(), "SYSTEM", "SYSTEM", null, s,
                System.currentTimeMillis(), null);
    }
}
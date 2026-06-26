package com.example.im.service;

import com.example.common.ApiException;                  // 业务异常
import com.example.im.domain.ChatMessage;                // 消息实体
import com.example.im.domain.ChatSession;                // 会话实体
import com.example.im.domain.SessionStatus;              // 会话状态枚举
import com.example.im.repo.ChatMessageRepo;              // 消息仓储
import com.example.im.repo.ChatSessionRepo;              // 会话仓储
import lombok.RequiredArgsConstructor;                   // Lombok 自动注入
import lombok.extern.slf4j.Slf4j;                        // 日志
import org.springframework.stereotype.Service;            // Spring 业务组件
import org.springframework.transaction.annotation.Transactional; // 事务

import java.time.LocalDate;                               // 日期
import java.time.LocalDateTime;                           // 日期时间
import java.time.ZoneId;                                 // 时区
import java.util.HashMap;                                // Map
import java.util.List;                                   // List
import java.util.Map;                                    // Map

/**
 * IM 会话业务服务（v1.9.0 完整注释版）
 *
 * <p>核心职责：
 * <ul>
 *   <li>会话生命周期：getOrCreate / transferToQueue / acceptByAgent / hangup</li>
 *   <li>状态机校验：ROBOT → QUEUED → IN_SESSION → ENDED</li>
 *   <li>查询：findActive / listByCustomer / listByAgent / stats</li>
 *   <li>系统消息：saveSystemMsg（发送方=SYSTEM 的消息）</li>
 * </ul>
 *
 * <p>真实业务场景：
 * <ol>
 *   <li>客户首次进入 → 创建 ROBOT 会话</li>
 *   <li>客户请求转人工 → QUEUED 排队</li>
 *   <li>坐席接听 → IN_SESSION，会话绑定坐席</li>
 *   <li>任一方挂断 → ENDED</li>
 *   <li>如果 5 分钟无活动 → 自动结束（定时任务）</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    /** 会话仓储 */
    private final ChatSessionRepo sessionRepo;

    /** 消息仓储 */
    private final ChatMessageRepo messageRepo;

    /** 审计服务 */
    private final AuditService auditService;

    /**
     * 获取或创建活跃会话（默认 ROBOT 状态）
     *
     * @param customerId 客户 ID
     * @return 活跃会话（ROBOT/QUEUED/IN_SESSION/TRADE_CHECKING）
     */
    @Transactional
    public ChatSession getOrCreate(String customerId) {
        return sessionRepo.findActiveByCustomer(customerId).orElseGet(() -> {
            // 1) 不存在 → 新建 ROBOT 会话
            ChatSession s = new ChatSession();
            s.setCustomerId(customerId);
            s.setStatus(SessionStatus.ROBOT);
            s.setCreatedAt(LocalDateTime.now());
            s.setLastActiveAt(LocalDateTime.now());
            sessionRepo.insert(s);
            log.info("[Session] 新建会话: customer={} sessionId={}", customerId, s.getId());
            return s;
        });
    }

    /**
     * 客户转人工（ROBOT → QUEUED）
     *
     * @param customerId 客户 ID
     * @return 更新后的会话
     */
    @Transactional
    public ChatSession transferToQueue(String customerId) {
        // 1) 找活跃会话
        ChatSession s = activeOrThrow(customerId);

        // 2) 已是 QUEUED 则直接返回
        if (s.getStatus() == SessionStatus.QUEUED) return s;

        // 3) 仅 ROBOT 可转人工
        if (s.getStatus() != SessionStatus.ROBOT) {
            throw new ApiException(400, "当前状态不可转人工: " + s.getStatus());
        }

        // 4) 更新状态
        s.setStatus(SessionStatus.QUEUED);
        s.setQueuedAt(LocalDateTime.now());
        s.setLastActiveAt(LocalDateTime.now());
        sessionRepo.updateById(s);

        // 5) 审计
        auditService.log("TRANSFER_QUEUE", "SESSION", String.valueOf(s.getId()),
                customerId, "CUSTOMER", null);
        log.info("[Session] customer={} 转人工排队 sessionId={}", customerId, s.getId());
        return s;
    }

    /**
     * 坐席接听（QUEUED → IN_SESSION）
     *
     * @param agentUsername 坐席账号
     * @param sessionId     会话 ID（可空，自动取最早排队）
     * @return 更新后的会话
     */
    @Transactional
    public ChatSession acceptByAgent(String agentUsername, Long sessionId) {
        // 1) 找会话
        ChatSession s;
        if (sessionId == null) {
            // 自动取最早排队的
            List<ChatSession> queue = sessionRepo.findByStatus(SessionStatus.QUEUED);
            if (queue.isEmpty()) throw new ApiException(404, "当前无排队客户");
            s = queue.get(0);
        } else {
            s = sessionRepo.findById(sessionId)
                    .orElseThrow(() -> new ApiException(404, "会话不存在"));
        }

        // 2) 校验状态
        if (s.getStatus() != SessionStatus.QUEUED) {
            throw new ApiException(400, "该会话不在排队: " + s.getStatus());
        }

        // 3) 更新状态
        s.setAgentUsername(agentUsername);
        s.setStatus(SessionStatus.IN_SESSION);
        s.setAcceptedAt(LocalDateTime.now());
        s.setLastActiveAt(LocalDateTime.now());
        sessionRepo.updateById(s);

        // 4) 写入系统消息
        saveSystemMsg(s, "坐席 " + agentUsername + " 已接入");

        // 5) 审计
        auditService.log("ACCEPT", "SESSION", String.valueOf(s.getId()),
                agentUsername, "AGENT", null);
        log.info("[Session] 坐席 {} 接听 sessionId={}", agentUsername, s.getId());
        return s;
    }

    /**
     * 挂断（任一方都可触发 → ENDED）
     *
     * @param who       "CUSTOMER" / "AGENT" / "ADMIN"
     * @param whoId     发起方 ID
     * @param sessionId 会话 ID
     * @return 更新后的会话
     */
    @Transactional
    public ChatSession hangup(String who, String whoId, Long sessionId) {
        // 1) 找会话
        ChatSession s = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ApiException(404, "会话不存在"));

        // 2) 幂等：已结束直接返回
        if (s.getStatus() == SessionStatus.ENDED) return s;

        // 3) 更新
        s.setStatus(SessionStatus.ENDED);
        s.setEndedAt(LocalDateTime.now());
        s.setEndedBy(who);
        s.setLastActiveAt(LocalDateTime.now());
        sessionRepo.updateById(s);

        // 4) 系统消息
        saveSystemMsg(s, "会话已结束（" + who + " 挂断）");

        // 5) 审计
        auditService.log("HANGUP", "SESSION", String.valueOf(s.getId()),
                whoId, who, null);
        log.info("[Session] sessionId={} 已结束（{} 挂断）", sessionId, who);
        return s;
    }

    /**
     * 管理员强制挂断
     */
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

    // ==================== 查询 ====================

    /** 按 ID 查 */
    public ChatSession findById(Long id) {
        return sessionRepo.findById(id).orElse(null);
    }

    /** 查客户的活跃会话（不存在抛 404） */
    public ChatSession findActive(String customerId) {
        return activeOrThrow(customerId);
    }

    /** 排队列表 */
    public List<ChatSession> listQueue() {
        return sessionRepo.findByStatus(SessionStatus.QUEUED);
    }

    /** 客户的所有会话 */
    public List<ChatSession> listByCustomer(String customerId) {
        return sessionRepo.findByCustomerId(customerId);
    }

    /** 坐席的所有会话 */
    public List<ChatSession> listByAgent(String agentUsername) {
        return sessionRepo.findByAgentUsername(agentUsername);
    }

    /** 实时看板统计 */
    public Map<String, Object> stats() {
        Map<String, Object> m = new HashMap<>();
        LocalDateTime startOfToday = LocalDate.now(ZoneId.systemDefault()).atStartOfDay();
        List<ChatSession> all = sessionRepo.findAll();
        m.put("totalSessions", all.size());
        m.put("queued", all.stream().filter(x -> x.getStatus() == SessionStatus.QUEUED).count());
        m.put("inSession", all.stream().filter(x -> x.getStatus() == SessionStatus.IN_SESSION).count());
        m.put("todaySessions", all.stream()
                .filter(x -> x.getCreatedAt() != null && x.getCreatedAt().isAfter(startOfToday)).count());
        m.put("todayEnded", all.stream()
                .filter(x -> x.getStatus() == SessionStatus.ENDED
                        && x.getEndedAt() != null && x.getEndedAt().isAfter(startOfToday)).count());
        return m;
    }

    /** 会话所有消息 */
    public List<ChatMessage> messagesOf(Long sessionId) {
        return messageRepo.findBySessionIdOrderByIdAsc(sessionId);
    }

    // ==================== 内部辅助 ====================

    /** 内部：获取活跃会话，不存在抛 404 */
    ChatSession activeOrThrow(String customerId) {
        return sessionRepo.findActiveByCustomer(customerId)
                .orElseThrow(() -> new ApiException(404, "无活跃会话"));
    }

    /**
     * 写一条系统消息（FROM=SYSTEM）
     */
    public ChatMessage saveSystemMsg(ChatSession s, String content) {
        ChatMessage m = new ChatMessage();
        m.setSessionId(s.getId());
        m.setFromUser("SYSTEM");
        m.setFromRole("SYSTEM");
        m.setType("SYSTEM");
        m.setContent(content);
        m.setCreatedAt(LocalDateTime.now());
        messageRepo.insert(m);
        // 同步更新会话最后活跃时间
        s.setLastActiveAt(LocalDateTime.now());
        sessionRepo.updateById(s);
        return m;
    }
}
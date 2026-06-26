package com.example.im.service;

import com.example.common.ApiException;                  // 业务异常
import com.example.im.domain.Ticket;                     // 工单实体
import com.example.im.domain.TicketReply;                // 工单回复实体
import com.example.im.repo.TicketMapper;                 // 工单 Mapper
import com.example.im.repo.TicketReplyMapper;            // 工单回复 Mapper
import lombok.RequiredArgsConstructor;                   // Lombok 自动注入
import lombok.extern.slf4j.Slf4j;                        // 日志
import org.springframework.stereotype.Service;            // Spring 业务组件
import org.springframework.transaction.annotation.Transactional; // 事务

import java.time.LocalDateTime;                           // 时间
import java.util.List;                                    // 列表
import java.util.Map;                                     // 通用 map

/**
 * 工单业务服务（v1.9.0）
 *
 * <p>提供：
 * <ul>
 *   <li>创建工单（客户自助 / 坐席代客 / 从会话一键建单）</li>
 *   <li>分配工单（管理员手动 / 自动派单）</li>
 *   <li>状态流转（PROCESSING / RESOLVED / CLOSED / CANCELLED）</li>
 *   <li>SLA 倒计时（按优先级）</li>
 *   <li>回复工单</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    /** 工单 Mapper */
    private final TicketMapper ticketMapper;

    /** 工单回复 Mapper */
    private final TicketReplyMapper replyMapper;

    /** SLA 时长（小时）按优先级 */
    private static final Map<String, Integer> SLA_HOURS = Map.of(
            "URGENT", 1,
            "HIGH",   4,
            "NORMAL", 24,
            "LOW",    72
    );

    /**
     * 创建工单
     *
     * @param customerId   客户 ID
     * @param title        标题
     * @param description  描述
     * @param category     分类：GENERAL / COMPLAINT / CONSULT / BUG
     * @param priority     优先级：LOW / NORMAL / HIGH / URGENT
     * @param sessionId    关联会话 ID（可空）
     * @return 新建的工单
     */
    @Transactional
    public Ticket create(String customerId, String title, String description,
                         String category, String priority, Long sessionId) {
        // 1) 校验参数
        if (customerId == null || customerId.isBlank()) throw new ApiException(400, "customerId 必填");
        if (title == null || title.isBlank()) throw new ApiException(400, "title 必填");

        // 2) 构造工单实体
        Ticket t = new Ticket();
        t.setTicketNo("TK" + System.currentTimeMillis() + (int) (Math.random() * 1000));
        t.setCustomerId(customerId);
        t.setTitle(title);
        t.setDescription(description);
        t.setCategory(category != null ? category : "GENERAL");
        t.setPriority(priority != null ? priority : "NORMAL");
        t.setStatus("OPEN");
        // SLA = now + (sla_hours of priority)
        int hours = SLA_HOURS.getOrDefault(t.getPriority(), 24);
        t.setSlaDeadline(LocalDateTime.now().plusHours(hours));

        // 3) 关联会话（可选）
        t.setSessionId(sessionId);

        // 4) 入库
        ticketMapper.insert(t);
        log.info("[Ticket] created ticketNo={} customer={} priority={}", t.getTicketNo(), customerId, t.getPriority());
        return t;
    }

    /**
     * 分配工单给坐席
     *
     * @param ticketNo      工单号
     * @param agentUsername 坐席账号
     */
    @Transactional
    public Ticket assign(String ticketNo, String agentUsername) {
        Ticket t = mustGet(ticketNo);
        // 仅 OPEN 状态可分配
        if (!"OPEN".equals(t.getStatus())) {
            throw new ApiException(400, "工单当前状态不可分配: " + t.getStatus());
        }
        t.setAgentUsername(agentUsername);
        t.setStatus("ASSIGNED");
        ticketMapper.updateById(t);
        log.info("[Ticket] {} assigned to {}", ticketNo, agentUsername);
        return t;
    }

    /**
     * 开始处理（ASSIGNED → PROCESSING）
     */
    @Transactional
    public Ticket startProcessing(String ticketNo) {
        Ticket t = mustGet(ticketNo);
        if (!"ASSIGNED".equals(t.getStatus()) && !"OPEN".equals(t.getStatus())) {
            throw new ApiException(400, "工单当前状态不可开始处理: " + t.getStatus());
        }
        t.setStatus("PROCESSING");
        ticketMapper.updateById(t);
        return t;
    }

    /**
     * 标记解决
     */
    @Transactional
    public Ticket resolve(String ticketNo) {
        Ticket t = mustGet(ticketNo);
        if (!"PROCESSING".equals(t.getStatus()) && !"ASSIGNED".equals(t.getStatus())) {
            throw new ApiException(400, "工单当前状态不可标记解决: " + t.getStatus());
        }
        t.setStatus("RESOLVED");
        t.setResolvedAt(LocalDateTime.now());
        ticketMapper.updateById(t);
        return t;
    }

    /**
     * 关闭工单
     */
    @Transactional
    public Ticket close(String ticketNo) {
        Ticket t = mustGet(ticketNo);
        if (!"RESOLVED".equals(t.getStatus())) {
            throw new ApiException(400, "工单当前状态不可关闭: " + t.getStatus());
        }
        t.setStatus("CLOSED");
        t.setClosedAt(LocalDateTime.now());
        ticketMapper.updateById(t);
        return t;
    }

    /**
     * 取消工单（客户主动 / 管理员）
     */
    @Transactional
    public Ticket cancel(String ticketNo, String reason) {
        Ticket t = mustGet(ticketNo);
        if ("CLOSED".equals(t.getStatus()) || "CANCELLED".equals(t.getStatus())) {
            throw new ApiException(400, "工单已结束，不可取消");
        }
        t.setStatus("CANCELLED");
        ticketMapper.updateById(t);
        // 自动追加一条系统回复
        TicketReply reply = new TicketReply();
        reply.setTicketId(t.getId());
        reply.setFromUser("SYSTEM");
        reply.setFromRole("SYSTEM");
        reply.setContent("工单已取消" + (reason != null ? "，原因：" + reason : ""));
        replyMapper.insert(reply);
        return t;
    }

    /**
     * 回复工单
     */
    @Transactional
    public TicketReply reply(Long ticketId, String fromUser, String fromRole, String content, String attachmentUrl) {
        // 1) 校验工单存在
        Ticket t = ticketMapper.selectById(ticketId);
        if (t == null) throw new ApiException(404, "工单不存在");

        // 2) 构造回复
        TicketReply reply = new TicketReply();
        reply.setTicketId(ticketId);
        reply.setFromUser(fromUser);
        reply.setFromRole(fromRole);
        reply.setContent(content);
        reply.setAttachmentUrl(attachmentUrl);
        replyMapper.insert(reply);

        // 3) 工单状态更新：如果客户回复了 → PROCESSING；坐席回复了 → 保持 PROCESSING / RESOLVED
        if ("CUSTOMER".equals(fromRole) && "RESOLVED".equals(t.getStatus())) {
            // 客户对解决方案不满意 → 重新打开
            t.setStatus("PROCESSING");
            ticketMapper.updateById(t);
        }
        return reply;
    }

    /**
     * 查工单详情
     */
    public Ticket findByTicketNo(String ticketNo) {
        return ticketMapper.findByTicketNo(ticketNo).orElse(null);
    }

    /**
     * 查工单的所有回复
     */
    public List<TicketReply> listReplies(Long ticketId) {
        return replyMapper.findByTicketIdOrderByIdAsc(ticketId);
    }

    /**
     * 客户的所有工单
     */
    public List<Ticket> listByCustomer(String customerId) {
        return ticketMapper.findByCustomerIdOrderByIdDesc(customerId);
    }

    /**
     * 坐席的所有工单
     */
    public List<Ticket> listByAgent(String agentUsername) {
        return ticketMapper.findByAgentUsernameOrderByIdDesc(agentUsername);
    }

    /**
     * 按状态查（用于坐席工作台）
     */
    public List<Ticket> listByStatus(String status) {
        return ticketMapper.findByStatusOrderByPriorityDesc(status);
    }

    /**
     * 私有：根据工单号获取，抛 404
     */
    private Ticket mustGet(String ticketNo) {
        return ticketMapper.findByTicketNo(ticketNo)
                .orElseThrow(() -> new ApiException(404, "工单不存在: " + ticketNo));
    }
}
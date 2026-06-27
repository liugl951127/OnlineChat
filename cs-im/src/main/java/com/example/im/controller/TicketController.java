package com.example.im.controller;

import com.example.common.ApiException;                // 业务异常
import com.example.common.ApiResponse;                // 统一返回
import com.example.common.SecurityContextHolder;       // 当前用户
import com.example.im.domain.Ticket;                   // 工单
import com.example.im.domain.TicketReply;              // 工单回复
import com.example.im.service.TicketService;           // 工单业务
import lombok.Data;                                    // Lombok
import lombok.RequiredArgsConstructor;                 // Lombok 自动注入
import org.springframework.web.bind.annotation.*;     // Spring MVC 注解

import java.util.List;                                  // 列表
import java.util.Map;                                   // map

/**
 * 工单 REST 接口（v1.9.0）
 *
 * <p>客户视角：
 * <ul>
 *   <li>POST /im/ticket/create — 创建工单</li>
 *   <li>GET  /im/ticket/list — 查我的工单</li>
 *   <li>GET  /im/ticket/{ticketNo} — 工单详情</li>
 *   <li>POST /im/ticket/{ticketNo}/reply — 客户回复</li>
 * </ul>
 *
 * <p>坐席视角：
 * <ul>
 *   <li>POST /im/ticket/{ticketNo}/assign — 认领工单</li>
 *   <li>POST /im/ticket/{ticketNo}/start — 开始处理</li>
 *   <li>POST /im/ticket/{ticketNo}/resolve — 标记解决</li>
 *   <li>POST /im/ticket/{ticketNo}/close — 关闭</li>
 *   <li>GET  /im/ticket/queue — 排队工单（按优先级）</li>
 *   <li>GET  /im/ticket/mine — 我的工单</li>
 * </ul>
 */
@RestController
@RequestMapping("/im/ticket")
@RequiredArgsConstructor
public class TicketController {

    /** 工单业务层 */
    private final TicketService ticketService;

    /**
     * 创建工单
     */
    @PostMapping("/create")
    public ApiResponse<Ticket> create(@RequestBody CreateReq req) {
        // 1) 校验登录
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");

        // 2) 客户只能为自己建单；坐席可为指定客户建单
        String customerId = req.getCustomerId() != null ? req.getCustomerId() : ctx.getUserId();

        // 3) 调 Service
        Ticket t = ticketService.create(
                customerId,
                req.getTitle(),
                req.getDescription(),
                req.getCategory(),
                req.getPriority(),
                req.getSessionId()
        );
        return ApiResponse.ok(t);
    }

    /**
     * 工单详情（含回复）
     */
    @GetMapping("/{ticketNo}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String ticketNo) {
        // 1) 查工单
        Ticket t = ticketService.findByTicketNo(ticketNo);
        if (t == null) throw new ApiException(404, "工单不存在");

        // 2) 查回复列表
        List<TicketReply> replies = ticketService.listReplies(t.getId());

        // 3) 组装返回
        return ApiResponse.ok(Map.of(
                "ticket", t,
                "replies", replies
        ));
    }

    /**
     * 客户的工单列表（强制从 SecurityContext 取，防越权）
     */
    @GetMapping("/list")
    public ApiResponse<List<Ticket>> listByCustomer(@RequestParam(required = false) String customerId) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        String cid = ctx.getUserId();
        boolean isStaff = "AGENT".equals(ctx.getRole()) || "ADMIN".equals(ctx.getRole());
        if (customerId != null && !customerId.isBlank() && !customerId.equals(cid)) {
            if (!isStaff) throw new ApiException(403, "无权查询他人工单");
            cid = customerId;
        }
        return ApiResponse.ok(ticketService.listByCustomer(cid));
    }

    /**
     * 排队工单（坐席工作台，按优先级排）
     */
    @GetMapping("/queue")
    public ApiResponse<List<Ticket>> queue() {
        return ApiResponse.ok(ticketService.listByStatus("OPEN"));
    }

    /**
     * 我的工单（坐席）
     */
    @GetMapping("/mine")
    public ApiResponse<List<Ticket>> mine() {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        return ApiResponse.ok(ticketService.listByAgent(ctx.getUserId()));
    }

    /**
     * 认领工单（坐席 OPEN → ASSIGNED）
     */
    @PostMapping("/{ticketNo}/assign")
    public ApiResponse<Ticket> assign(@PathVariable String ticketNo) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        return ApiResponse.ok(ticketService.assign(ticketNo, ctx.getUserId()));
    }

    /**
     * 开始处理（ASSIGNED → PROCESSING）
     */
    @PostMapping("/{ticketNo}/start")
    public ApiResponse<Ticket> start(@PathVariable String ticketNo) {
        return ApiResponse.ok(ticketService.startProcessing(ticketNo));
    }

    /**
     * 标记解决（PROCESSING → RESOLVED）
     */
    @PostMapping("/{ticketNo}/resolve")
    public ApiResponse<Ticket> resolve(@PathVariable String ticketNo) {
        return ApiResponse.ok(ticketService.resolve(ticketNo));
    }

    /**
     * 关闭工单（RESOLVED → CLOSED）
     */
    @PostMapping("/{ticketNo}/close")
    public ApiResponse<Ticket> close(@PathVariable String ticketNo) {
        return ApiResponse.ok(ticketService.close(ticketNo));
    }

    /**
     * 取消工单（OPEN/ASSIGNED/PROCESSING → CANCELLED）
     */
    @PostMapping("/{ticketNo}/cancel")
    public ApiResponse<Ticket> cancel(@PathVariable String ticketNo, @RequestBody(required = false) CancelReq req) {
        String reason = req != null ? req.getReason() : null;
        return ApiResponse.ok(ticketService.cancel(ticketNo, reason));
    }

    /**
     * 回复工单
     */
    @PostMapping("/{ticketNo}/reply")
    public ApiResponse<TicketReply> reply(@PathVariable String ticketNo, @RequestBody ReplyReq req) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");

        // 1) 查工单 ID
        Ticket t = ticketService.findByTicketNo(ticketNo);
        if (t == null) throw new ApiException(404, "工单不存在");

        // 2) 调 Service
        TicketReply reply = ticketService.reply(
                t.getId(),
                ctx.getUserId(),
                ctx.getRole(),
                req.getContent(),
                req.getAttachmentUrl()
        );
        return ApiResponse.ok(reply);
    }

    /**
     * 创建工单请求体
     */
    @Data
    public static class CreateReq {
        /** 客户 ID（坐席代客时填，客户自建可为空，默认自己） */
        private String customerId;
        /** 标题 */
        private String title;
        /** 描述 */
        private String description;
        /** 分类：GENERAL/COMPLAINT/CONSULT/BUG */
        private String category;
        /** 优先级：LOW/NORMAL/HIGH/URGENT */
        private String priority;
        /** 关联会话 ID（可空） */
        private Long sessionId;
    }

    /**
     * 取消工单请求体
     */
    @Data
    public static class CancelReq {
        private String reason;
    }

    /**
     * 回复工单请求体
     */
    @Data
    public static class ReplyReq {
        private String content;
        private String attachmentUrl;
    }
}
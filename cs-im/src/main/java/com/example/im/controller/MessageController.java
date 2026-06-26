package com.example.im.controller;

import com.example.common.ApiException;          // 业务异常
import com.example.common.ApiResponse;          // 统一返回包装
import com.example.common.SecurityContextHolder; // 从 Header 取当前用户身份
import com.example.im.domain.ChatMessage;       // 消息实体
import com.example.im.repo.ChatMessageRepo;     // 消息仓储
import com.example.im.service.MessageService;   // 消息业务
import com.example.im.service.SessionService;   // 会话业务
import com.example.im.domain.ChatSession;       // 会话实体
import com.example.im.domain.SessionStatus;     // 会话状态枚举
import lombok.Data;                              // Lombok 自动 getter/setter
import lombok.RequiredArgsConstructor;           // Lombok 自动构造 final 字段注入
import org.springframework.web.bind.annotation.*; // Spring MVC 注解

import java.util.List;                           // 列表返回类型
import java.util.Map;                            // 通用 map
import java.util.HashMap;                        // Map 实现

/**
 * 消息 REST 接口（v1.9.0 增强版）
 *
 * <p>核心场景：
 * <ul>
 *   <li>客户 / 坐席 通过 HTTP POST /im/message/send 实时发送消息</li>
 *   <li>接收方通过 HTTP GET /im/message/poll 长轮询拉取新消息</li>
 *   <li>未登录 / 未连 WebSocket 时，落 Kafka 离线队列</li>
 *   <li>无坐席在线 → 客户端轮询时返回 NO_AGENT_AVAILABLE</li>
 *   <li>视频回溯 hook：发送的消息自动入库，供前端 / 后台播放</li>
 * </ul>
 */
@RestController                       // 标记为 REST 控制器（@ResponseBody 默认）
@RequestMapping("/im/message")        // 一级路由前缀
@RequiredArgsConstructor              // Lombok 自动生成构造方法注入 final 字段
public class MessageController {

    /** 消息业务层（含 XSS 净化、限流、签名、Kafka 推送） */
    private final MessageService messageService;

    /** 消息仓储（直接查询用） */
    private final ChatMessageRepo messageRepo;

    /** 会话业务层（用于实时拉取时校验坐席在线状态） */
    private final SessionService sessionService;

    /**
     * 发送消息（HTTP 实时）
     *
     * <p>适用场景：客户 / 坐席在前端通过 fetch / axios 发送消息。
     * 走 Kafka 异步推送给接收方；接收方通过 poll 接口拉取。
     *
     * @param req 请求体 { sessionId, content, type }
     * @return 持久化后的 ChatMessage
     */
    @PostMapping("/send")                                       // POST /im/message/send
    public ApiResponse<ChatMessage> send(@RequestBody SendReq req) {
        // 1) 校验登录态（从 Gateway 透传的 X-User-* Header）
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");

        // 2) 校验参数
        if (req.getSessionId() == null) throw new ApiException(400, "sessionId 必填");
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new ApiException(400, "消息内容不能为空");
        }

        // 3) 走 Service（XSS 净化 + 限流 + 签名 + 入库 + Kafka 推送）
        ChatMessage m = messageService.send(
                req.getSessionId(),                              // 会话 ID
                ctx.getUserId(),                                 // 发送方 ID
                ctx.getDisplayName() != null ? ctx.getDisplayName() : ctx.getUserId(),  // 发送方显示名
                ctx.getRole(),                                   // 角色：CUSTOMER / AGENT
                req.getContent(),                                // 消息文本（已 XSS 净化）
                req.getType() != null ? req.getType() : "TEXT"   // 消息类型：TEXT / IMAGE / FILE / RICH
        );

        // 4) 同步推送给接收方（用 Kafka，失败降级不报错）
        //    这里 messageService.send 内部已经推 Kafka，无需重复
        return ApiResponse.ok(m);
    }

    /**
     * 实时拉取（HTTP 轮询，长轮询简化版）
     *
     * <p>前端每 1-3 秒调用一次：GET /im/message/poll?sessionId=xxx&lastId=yyy
     * 返回 lastId 之后的新消息；若会话中没有坐席在线，返回 status=NO_AGENT_AVAILABLE。
     *
     * @param sessionId 会话 ID
     * @param lastId    客户端最后收到的消息 ID（首次传 0）
     * @return { messages: [...], status: "OK"|"NO_AGENT_AVAILABLE" }
     */
    @GetMapping("/poll")                                        // GET /im/message/poll
    public ApiResponse<Map<String, Object>> poll(@RequestParam Long sessionId,
                                                   @RequestParam(defaultValue = "0") Long lastId) {
        // 1) 校验登录
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");

        // 2) 查会话
        ChatSession session = sessionService.findById(sessionId);
        if (session == null) throw new ApiException(404, "会话不存在");

        // 3) 查 lastId 之后的新消息（按 ID 升序）
        List<ChatMessage> newMsgs = messageRepo.findBySessionIdAfter(sessionId, lastId);

        // 4) 组装返回
        Map<String, Object> resp = new HashMap<>();
        resp.put("messages", newMsgs);
        resp.put("lastId", newMsgs.isEmpty() ? lastId : newMsgs.get(newMsgs.size() - 1).getId());

        // 5) 状态判断
        //    - 客户视角：会话 status != IN_SESSION 且无坐席 → 提示无坐席
        //    - 坐席视角：始终 OK
        if ("CUSTOMER".equals(ctx.getRole())) {
            SessionStatus sessionStatus = session.getStatus();
            boolean noAgent = sessionStatus == SessionStatus.ROBOT
                    || (sessionStatus == SessionStatus.QUEUED && session.getAgentUsername() == null);
            resp.put("status", noAgent ? "NO_AGENT_AVAILABLE" : "OK");
        } else {
            resp.put("status", "OK");
        }

        return ApiResponse.ok(resp);
    }

    /**
     * 撤回消息（2 分钟内）
     */
    @PostMapping("/{id}/recall")                                // POST /im/message/{id}/recall
    public ApiResponse<ChatMessage> recall(@PathVariable Long id) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        // 走 Service（含权限校验 + 时间窗口校验）
        messageService.recall(id, ctx.getUserId());
        // 返回最新消息对象
        return ApiResponse.ok(messageRepo.findById(id).orElse(null));
    }

    /**
     * 历史消息（分页 + 时间范围）
     *
     * @param sessionId 会话 ID
     * @param limit     返回消息条数（默认 100，最大 500）
     * @return 消息列表（按 ID 升序）
     */
    @GetMapping("/history/{sessionId}")                         // GET /im/message/history/{sessionId}
    public ApiResponse<List<ChatMessage>> history(@PathVariable Long sessionId,
                                                   @RequestParam(defaultValue = "100") int limit) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        // 限流 + XSS 净化后的历史
        return ApiResponse.ok(messageService.history(sessionId, Math.min(limit, 500)));
    }

    /**
     * 视频回溯数据源
     *
     * <p>前端播放器根据 startTime/endTime 拉取消息，渲染为视频字幕。
     *
     * @param sessionId 会话 ID
     * @param startTime 起始时间（ISO 字符串，可选）
     * @param endTime   结束时间（ISO 字符串，可选）
     * @return 消息列表（含 createdAt 时间戳，供前端按帧播放）
     */
    @GetMapping("/replay/{sessionId}")                          // GET /im/message/replay/{sessionId}
    public ApiResponse<Map<String, Object>> replay(@PathVariable Long sessionId,
                                                    @RequestParam(required = false) String startTime,
                                                    @RequestParam(required = false) String endTime) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");

        // 1) 查会话
        ChatSession session = sessionService.findById(sessionId);
        if (session == null) throw new ApiException(404, "会话不存在");

        // 2) 查时间范围内消息
        List<ChatMessage> msgs = messageRepo.findBySessionIdAndTimeRange(sessionId, startTime, endTime);

        // 3) 组装回溯数据
        Map<String, Object> resp = new HashMap<>();
        resp.put("sessionId", sessionId);
        resp.put("customerId", session.getCustomerId());
        resp.put("agentUsername", session.getAgentUsername());
        resp.put("startTime", session.getCreatedAt());
        resp.put("endTime", session.getEndedAt());
        resp.put("messages", msgs);

        return ApiResponse.ok(resp);
    }

    /**
     * 发送请求体
     */
    @Data
    public static class SendReq {
        /** 会话 ID */
        private Long sessionId;
        /** 消息文本 */
        private String content;
        /** 消息类型：TEXT / IMAGE / FILE / RICH / VIDEO，默认 TEXT */
        private String type;
    }
}
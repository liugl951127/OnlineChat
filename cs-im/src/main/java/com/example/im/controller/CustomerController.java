package com.example.im.controller;

import com.example.common.ApiException;
import com.example.common.ApiResponse;
import com.example.common.SecurityContextHolder;
import com.example.common.msg.OfflineMessageStore;
import com.example.common.msg.WsPushService;
import com.example.im.domain.ChatMessage;
import com.example.im.domain.ChatSession;
import com.example.im.service.MessageService;
import com.example.im.service.SessionService;
import com.example.im.service.FaqService;
import com.example.im.service.RobotEngine;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户 REST API（v1.9.0 完整版）
 *
 * <p>功能：
 * <ul>
 *   <li>会话管理：active / transfer-to-agent / hangup</li>
 *   <li>实时聊天：send + poll（HTTP 实时）</li>
 *   <li>离线消息：drain（拉取暂存消息）</li>
 *   <li>FAQ 浏览：search / top / byCategory</li>
 *   <li>视频回溯：replay</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/im/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final SessionService sessionService;
    private final MessageService messageService;
    private final OfflineMessageStore offlineStore;
    private final WsPushService wsPushService;
    private final FaqService faqService;
    private final RobotEngine robotEngine;

    /**
     * 查 / 创建活跃会话
     */
    @GetMapping("/session/active")
    public ApiResponse<ChatSession> active() {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        return ApiResponse.ok(sessionService.getOrCreate(ctx.getUserId()));
    }

    /**
     * 客户请求转人工
     */
    @PostMapping("/session/transfer-to-agent")
    public ApiResponse<ChatSession> transfer() {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        return ApiResponse.ok(sessionService.transferToQueue(ctx.getUserId()));
    }

    /**
     * 客户挂断
     */
    @PostMapping("/session/hangup")
    public ApiResponse<ChatSession> hangup() {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        ChatSession s = sessionService.findActive(ctx.getUserId());
        return ApiResponse.ok(sessionService.hangup("CUSTOMER", ctx.getUserId(), s.getId()));
    }

    /**
     * 客户发消息：先走机器人 → 无坐席时落离线 + 提示"当前无坐席"
     */
    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody ChatReq req) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        String cid = ctx.getUserId();
        ChatSession s = sessionService.getOrCreate(cid);

        // 1) 存档客户消息
        ChatMessage custMsg = messageService.send(s.getId(), cid, cid, "CUSTOMER", req.getText(), "TEXT");

        // 2) 状态判断
        Map<String, Object> resp = new HashMap<>();
        resp.put("message", custMsg);
        resp.put("session", s);

        // 3) 关键词检测：转人工
        if (req.getText() != null && (req.getText().contains("人工") || req.getText().contains("转接"))) {
            ChatSession queued = sessionService.transferToQueue(cid);
            resp.put("session", queued);
            resp.put("transferred", true);
            resp.put("tip", "正在为你排队等待坐席，请稍候...");
            return ApiResponse.ok(resp);
        }

        // 4) ROBOT 状态：调机器人 → 命中 FAQ / 关键词回复
        if ("ROBOT".equals(s.getStatus())) {
            com.example.common.RichMessage botReply = robotEngine.handle(cid, req.getText());
            if (botReply != null) {
                // 把机器人回复也写入消息流
                ChatMessage botMsg = messageService.send(s.getId(), "ROBOT", "智能客服", "ROBOT",
                        botReply.getText() != null ? botReply.getText() : "[富文本]", "RICH");
                resp.put("robotReply", botReply);
                resp.put("robotMessage", botMsg);
            }
            // 检查是否有坐席在线（简化：ROBOT 状态肯定没有坐席）
            resp.put("hasAgent", false);
            resp.put("tip", "当前为智能客服模式，如需人工服务请说\"人工\"");
        } else if ("QUEUED".equals(s.getStatus())) {
            resp.put("hasAgent", false);
            resp.put("tip", "正在为你排队等待坐席...");
        } else if ("IN_SESSION".equals(s.getStatus())) {
            resp.put("hasAgent", true);
            resp.put("agentUsername", s.getAgentUsername());
            resp.put("tip", "坐席 " + s.getAgentUsername() + " 正在为你服务");
        } else {
            resp.put("hasAgent", false);
            resp.put("tip", "当前无坐席在线（" + s.getStatus() + "）");
        }

        return ApiResponse.ok(resp);
    }

    /**
     * v2.3.0: drain 离线消息 (按 userId)
     */
    @GetMapping("/offline/drain")
    public ApiResponse<List<String>> drainOffline() {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        return ApiResponse.ok(offlineStore.drain(ctx.getUserId()));
    }

    /**
     * v2.3.0: 查离线消息数量 (前端小红点)
     */
    @GetMapping("/offline/size")
    public ApiResponse<Long> offlineSize() {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        return ApiResponse.ok(offlineStore.count(ctx.getUserId()));
    }

    /**
     * v2.3.0: WS 在线状态 (前端进站确认是否连得上)
     */
    @GetMapping("/ws/status")
    public ApiResponse<Map<String, Object>> wsStatus() {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        boolean local = wsPushService.isLocalOnline(ctx.getUserId());
        Map<String, Object> ret = new HashMap<>();
        ret.put("localOnline", local);
        return ApiResponse.ok(ret);
    }

    /**
     * FAQ 搜索（前端 KnowledgeBase 页用）
     */
    @GetMapping("/faq/search")
    public ApiResponse<List<com.example.im.domain.Faq>> searchFaq(@RequestParam String keyword) {
        return ApiResponse.ok(faqService.search(keyword));
    }

    /**
     * 热门 FAQ
     */
    @GetMapping("/faq/top")
    public ApiResponse<List<com.example.im.domain.Faq>> topFaqs() {
        return ApiResponse.ok(faqService.topFaqs());
    }

    /**
     * 我的会话列表
     */
    @GetMapping("/sessions")
    public ApiResponse<List<ChatSession>> sessions() {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        return ApiResponse.ok(sessionService.listByCustomer(ctx.getUserId()));
    }

    /**
     * 会话消息历史
     */
    @GetMapping("/messages")
    public ApiResponse<List<ChatMessage>> messages(@RequestParam Long sessionId,
                                                   @RequestParam(defaultValue = "200") int limit) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        return ApiResponse.ok(messageService.history(sessionId, limit));
    }

    /**
     * 视频回溯
     */
    @GetMapping("/replay/{sessionId}")
    public ApiResponse<List<com.example.im.domain.ChatMessage>> replay(@PathVariable Long sessionId) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        return ApiResponse.ok(messageService.replay(sessionId, null, null));
    }

    @Data
    public static class ChatReq {
        private String text;
    }
}
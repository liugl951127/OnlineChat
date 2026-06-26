package com.example.im.controller;

import com.example.common.ApiResponse;
import com.example.common.RichMessage;
import com.example.common.SecurityContextHolder;
import com.example.im.client.RobotClient;
import com.example.im.client.TradeClient;
import com.example.im.domain.ChatMessage;
import com.example.im.domain.ChatSession;
import com.example.im.service.AuditService;
import com.example.im.service.MessageService;
import com.example.im.service.OfflinePushService;
import com.example.im.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 客户 REST API（含机器人应答、账单/产品查询、转人工）
 */
@Slf4j
@RestController
@RequestMapping("/im/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final SessionService sessionService;
    private final AuditService auditService;
    private final OfflinePushService offlinePush;
    private final RobotClient robotClient;
    private final TradeClient tradeClient;
    private final MessageService messageService;
    private final ObjectMapper json = new ObjectMapper();

    @GetMapping("/session/active")
    public ApiResponse<ChatSession> active() {
        String cid = SecurityContextHolder.requireUserId();
        return ApiResponse.ok(sessionService.getOrCreate(cid));
    }

    @PostMapping("/session/transfer-to-agent")
    public ApiResponse<ChatSession> transfer() {
        String cid = SecurityContextHolder.requireUserId();
        return ApiResponse.ok(sessionService.transferToQueue(cid));
    }

    @PostMapping("/session/hangup")
    public ApiResponse<ChatSession> hangup() {
        String cid = SecurityContextHolder.requireUserId();
        ChatSession s = sessionService.findActive(cid);
        return ApiResponse.ok(sessionService.hangup("CUSTOMER", cid, s.getId()));
    }

    /** 客户发消息：先经过机器人 */
    @PostMapping("/chat")
    public ApiResponse<Object> chat(@RequestBody ChatReq req) {
        String cid = SecurityContextHolder.requireUserId();
        ChatSession s = sessionService.getOrCreate(cid);

        // 1) 存档客户消息
        sessionService.saveAndBroadcast(s, "CUSTOMER", req.getText(), cid);

        // 2) 如果在 ROBOT 状态 → 调机器人
        if (s.getStatus() == com.example.im.domain.SessionStatus.ROBOT) {
            var resp = robotClient.chat(new RobotClient.ChatReq(cid, req.getText()));
            RichMessage rm = resp.getData();
            if (rm != null) {
                ChatMessage botMsg = sessionService.saveRich(s, "ROBOT", rm);
                // 触发离线推送（客户可能已经关闭页面）
                pushIfOffline(cid, "OA", rm);
            }
            // 检查是否触发转人工
            if (req.getText() != null && (req.getText().contains("人工") || req.getText().contains("转接"))) {
                ChatSession queued = sessionService.transferToQueue(cid);
                return ApiResponse.ok(Map.of(
                        "robotReply", rm,
                        "session", queued,
                        "transferred", true));
            }
            return ApiResponse.ok(Map.of("robotReply", rm, "session", s, "transferred", false));
        }

        // IN_SESSION：交给 STOMP，不走 REST
        return ApiResponse.ok(Map.of("session", s, "transferred", false));
    }

    /** 历史回溯 */
    @GetMapping("/sessions")
    public ApiResponse<List<ChatSession>> sessions() {
        String cid = SecurityContextHolder.requireUserId();
        return ApiResponse.ok(sessionService.listByCustomer(cid));
    }

    @GetMapping("/messages")
    public ApiResponse<Map<String, Object>> messages(@RequestParam Long sessionId) {
        List<ChatMessage> msgs = sessionService.messagesOf(sessionId);
        Map<Long, Map<String, Long>> reactions = messageService.reactionsOfMessages(sessionId);
        return ApiResponse.ok(Map.of("messages", msgs, "reactions", reactions));
    }

    /** 账单查询（富文本） */
    @GetMapping("/bills")
    public ApiResponse<RichMessage> bills(@RequestParam(defaultValue = "7") int days) {
        String cid = SecurityContextHolder.requireUserId();
        var resp = tradeClient.recentBills(cid, days);
        // 委托 trade 服务返回结构，这里直接包装成 RichMessage
        RichMessage rm = RichMessage.bill("最近 " + days + " 天账单", (List<Map<String, Object>>) resp.getData());
        return ApiResponse.ok(rm);
    }

    /** 产品查询（富文本） */
    @GetMapping("/products")
    public ApiResponse<List<RichMessage>> products(@RequestParam(required = false) String keyword) {
        var resp = tradeClient.products(keyword);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) resp.getData();
        List<RichMessage> rms = list.stream().map(m ->
                RichMessage.product(
                        (String) m.get("name"),
                        (String) m.get("desc"),
                        ((Number) m.get("rate")).doubleValue(),
                        (String) m.get("period"))).toList();
        return ApiResponse.ok(rms);
    }

    private void pushIfOffline(String cid, String channel, RichMessage rm) {
        if (!offlinePush.isOnline(cid)) {
            ObjectNode payload = json.createObjectNode();
            payload.put("title", "智能客服回复");
            payload.put("desc", rm.getText() == null ? "点击查看" : rm.getText());
            payload.put("link", "/customer/?token=auto");
            offlinePush.enqueue(cid, channel, payload);
        }
    }

    @Data
    public static class ChatReq { private String text; }
}
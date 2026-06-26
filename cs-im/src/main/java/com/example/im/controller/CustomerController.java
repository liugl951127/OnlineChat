package com.example.im.controller;

import com.example.common.ApiResponse;
import com.example.common.SecurityContextHolder;
import com.example.im.domain.ChatMessage;
import com.example.im.domain.ChatSession;
import com.example.im.service.AuditService;
import com.example.im.service.MessageService;
import com.example.im.service.SessionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 客户 REST API（v1.8.0 简化版）
 */
@Slf4j
@RestController
@RequestMapping("/im/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final SessionService sessionService;
    private final MessageService messageService;
    private final AuditService auditService;

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

    /** 客户发消息：先经过机器人（或转人工） */
    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody ChatReq req) {
        String cid = SecurityContextHolder.requireUserId();
        ChatSession s = sessionService.getOrCreate(cid);

        // 存档客户消息
        ChatMessage custMsg = messageService.send(s.getId(), cid, cid, "CUSTOMER", req.getText(), "TEXT");

        // ROBOT 状态：触发转人工的关键词检测
        if (s.getStatus() == com.example.im.domain.SessionStatus.ROBOT
                && req.getText() != null
                && (req.getText().contains("人工") || req.getText().contains("转接"))) {
            ChatSession queued = sessionService.transferToQueue(cid);
            return ApiResponse.ok(Map.of(
                    "message", custMsg,
                    "session", queued,
                    "transferred", true));
        }
        return ApiResponse.ok(Map.of("message", custMsg, "session", s, "transferred", false));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSession>> sessions() {
        String cid = SecurityContextHolder.requireUserId();
        return ApiResponse.ok(sessionService.listByCustomer(cid));
    }

    @GetMapping("/messages")
    public ApiResponse<List<ChatMessage>> messages(@RequestParam Long sessionId) {
        return ApiResponse.ok(sessionService.messagesOf(sessionId));
    }

    @Data
    public static class ChatReq { private String text; }
}
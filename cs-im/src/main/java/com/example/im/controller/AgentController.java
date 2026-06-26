package com.example.im.controller;

import com.example.common.ApiException;
import com.example.common.ApiResponse;
import com.example.common.SecurityContextHolder;
import com.example.im.domain.ChatSession;
import com.example.im.service.AuditService;
import com.example.im.service.SessionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 坐席 REST API（v1.9.0 完整版）
 *
 * <p>功能：
 * <ul>
 *   <li>查排队队列（按时间）</li>
 *   <li>接听客户（QUEUED → IN_SESSION）</li>
 *   <li>挂断会话</li>
 *   <li>查我的会话列表</li>
 *   <li>工作台统计（实时看板）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/im/agent")
@RequiredArgsConstructor
public class AgentController {

    private final SessionService sessionService;
    private final AuditService auditService;

    /**
     * 排队队列（QUEUED 状态的会话）
     */
    @GetMapping("/queue")
    public ApiResponse<List<ChatSession>> queue() {
        return ApiResponse.ok(sessionService.listQueue());
    }

    /**
     * 接听客户（不指定 sessionId 则取最早排队的）
     */
    @PostMapping("/accept")
    public ApiResponse<ChatSession> accept(@RequestBody(required = false) AcceptReq req) {
        // 1) 取当前坐席
        String agent = SecurityContextHolder.requireUserId();

        // 2) 接听
        Long sessionId = req != null ? req.getSessionId() : null;
        ChatSession s = sessionService.acceptByAgent(agent, sessionId);
        return ApiResponse.ok(s);
    }

    /**
     * 坐席挂断
     */
    @PostMapping("/hangup")
    public ApiResponse<ChatSession> hangup(@RequestBody HangupReq req) {
        String agent = SecurityContextHolder.requireUserId();
        return ApiResponse.ok(sessionService.hangup("AGENT", agent, req.getSessionId()));
    }

    /**
     * 我的会话列表
     */
    @GetMapping("/sessions")
    public ApiResponse<List<ChatSession>> mySessions() {
        String agent = SecurityContextHolder.requireUserId();
        return ApiResponse.ok(sessionService.listByAgent(agent));
    }

    /**
     * 工作台统计（实时看板）
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(sessionService.stats());
    }

    @Data
    public static class AcceptReq {
        /** 指定接听某个 session（可空，自动取最早排队） */
        private Long sessionId;
    }

    @Data
    public static class HangupReq {
        private Long sessionId;
    }
}
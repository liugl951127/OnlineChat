package com.example.im.controller;

import com.example.common.ApiResponse;
import com.example.common.SecurityContextHolder;
import com.example.im.domain.ChatSession;
import com.example.im.service.AuditService;
import com.example.im.service.SessionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/im/agent")
@RequiredArgsConstructor
public class AgentController {

    private final SessionService sessionService;
    private final AuditService auditService;

    @GetMapping("/queue")
    public ApiResponse<List<ChatSession>> queue() {
        return ApiResponse.ok(sessionService.listQueue());
    }

    @PostMapping("/accept")
    public ApiResponse<ChatSession> accept(@RequestBody AcceptReq req) {
        String agent = SecurityContextHolder.requireUserId();
        return ApiResponse.ok(sessionService.acceptByAgent(agent, req.getSessionId()));
    }

    @PostMapping("/hangup")
    public ApiResponse<ChatSession> hangup(@RequestBody HangupReq req) {
        String agent = SecurityContextHolder.requireUserId();
        return ApiResponse.ok(sessionService.hangup("AGENT", agent, req.getSessionId()));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSession>> mySessions() {
        String agent = SecurityContextHolder.requireUserId();
        return ApiResponse.ok(sessionService.listByAgent(agent));
    }

    @Data
    public static class AcceptReq { private Long sessionId; }
    @Data
    public static class HangupReq { private Long sessionId; }
}
package com.example.im.controller;

import com.example.common.ApiResponse;
import com.example.common.ApiException;
import com.example.common.SecurityContextHolder;
import com.example.im.domain.AuditLog;
import com.example.im.domain.ChatSession;
import com.example.im.repo.AuditLogRepo;
import com.example.im.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 后管 API：会话审核 + 审计日志查询
 */
@RestController
@RequestMapping("/im/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SessionService sessionService;
    private final AuditLogRepo auditLogRepo;

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSession>> allSessions(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        SecurityContextHolder.requireRole("ADMIN");
        Page<ChatSession> p = sessionService.pageAll(PageRequest.of(page, size));
        return ApiResponse.ok(p.getContent());
    }

    @PostMapping("/sessions/{id}/force-hangup")
    public ApiResponse<ChatSession> forceHangup(@PathVariable Long id,
                                                @RequestParam(required = false) String reason) {
        SecurityContextHolder.requireRole("ADMIN");
        if (reason == null || reason.isBlank()) throw new ApiException(400, "请填写强制挂断原因");
        return ApiResponse.ok(sessionService.forceHangup(id, reason));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<Page<AuditLog>> auditLogs(@RequestParam(required = false) String action,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        SecurityContextHolder.requireRole("ADMIN");
        Page<AuditLog> p = action == null
                ? auditLogRepo.findAllByOrderByIdDesc(PageRequest.of(page, size))
                : auditLogRepo.findByActionOrderByIdDesc(action, PageRequest.of(page, size));
        return ApiResponse.ok(p);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        SecurityContextHolder.requireRole("ADMIN");
        return ApiResponse.ok(sessionService.stats());
    }
}
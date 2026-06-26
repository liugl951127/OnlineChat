package com.example.im.service;

import com.example.im.domain.AuditLog;
import com.example.im.repo.AuditLogRepo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计日志服务（合规：所有敏感操作落库）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepo repo;

    @Async
    public void log(String action, String targetType, String targetId,
                    String userId, String userRole, String detail) {
        String ip = currentIp();
        String traceId = currentHeader("X-Trace-Id");
        repo.save(com.example.im.domain.AuditLog.builder()
                .action(action).targetType(targetType).targetId(targetId)
                .userId(userId).userRole(userRole)
                .detail(detail == null || detail.length() > 4000 ? detail.substring(0, 4000) : detail)
                .ip(ip).traceId(traceId).build());
    }

    private static String currentIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest req = attrs.getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            return xff != null ? xff.split(",")[0] : req.getRemoteAddr();
        } catch (Exception e) { return null; }
    }

    private static String currentHeader(String h) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            return attrs.getRequest().getHeader(h);
        } catch (Exception e) { return null; }
    }
}
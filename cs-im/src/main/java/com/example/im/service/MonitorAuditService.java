package com.example.im.service;

import com.example.im.domain.MonitorAudit;
import com.example.im.repo.MonitorAuditRepo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 录像访问审计 (v2.3.0 合规)
 *
 * <p>PBOC 17 号文 + 等保 2.0 三级: 录像访问必须全留痕, 6 个月留存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorAuditService {

    private final MonitorAuditRepo auditRepo;

    public void log(Long sessionId, String customerId, String operatorId, String operatorRole,
                    String action, Integer segmentFrom, Integer segmentTo, String extra,
                    HttpServletRequest req) {
        try {
            MonitorAudit a = new MonitorAudit();
            a.setSessionId(sessionId);
            a.setCustomerId(customerId);
            a.setOperatorId(operatorId);
            a.setOperatorRole(operatorRole);
            a.setOperatorIp(clientIp(req));
            a.setAction(action);
            a.setSegmentFrom(segmentFrom);
            a.setSegmentTo(segmentTo);
            a.setExtra(extra);
            a.setCreatedAt(LocalDateTime.now());
            auditRepo.insert(a);
        } catch (Exception e) {
            // 审计失败不能影响业务, 但必须打 error log
            log.error("[MonitorAudit] 审计写入失败 session={} action={}: {}",
                    sessionId, action, e.getMessage());
        }
    }

    private String clientIp(HttpServletRequest req) {
        if (req == null) return null;
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isEmpty()) return real;
        return req.getRemoteAddr();
    }

    /**
     * 查询某 session 的审计 (admin 用)
     */
    public java.util.List<MonitorAudit> listBySession(Long sessionId) {
        return auditRepo.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MonitorAudit>()
                        .eq("session_id", sessionId)
                        .orderByDesc("created_at"));
    }
}
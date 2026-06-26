package com.example.im.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 审计日志（内存 Map 占位，生产应入 MySQL）
 */
@Slf4j
@Service
public class AuditService {

    public void log(String action, String targetType, String targetId, String operator, String operatorRole, String detail) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("action", action);
        entry.put("targetType", targetType);
        entry.put("targetId", targetId);
        entry.put("operator", operator);
        entry.put("operatorRole", operatorRole);
        entry.put("detail", detail);
        entry.put("ts", LocalDateTime.now());
        log.info("[Audit] {}", entry);
    }
}
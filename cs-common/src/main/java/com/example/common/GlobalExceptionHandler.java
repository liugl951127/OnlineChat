package com.example.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApi(ApiException e) {
        return ResponseEntity.status(e.getCode()).body(ApiResponse.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 兜底异常（v2.2.35）：不把原始异常 message 返回前端（防信息泄漏）。
     * 生产环境推荐暴露 traceId 给前端以便用户报障。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleAny(Exception e) {
        String traceId = java.util.UUID.randomUUID().toString().substring(0, 8);
        log.error("[unhandled error] traceId={} msg={}", traceId, e.getMessage(), e);
        return ResponseEntity.status(500).body(
                ApiResponse.fail(500, "服务异常，请稍后重试 (traceId=" + traceId + ")"));
    }
}
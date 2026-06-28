package com.example.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApi(ApiException e) {
        // v2.2.80: 带 data 的 ApiException 把 data 一起返回 (公众号关注二维码场景)
        Object data = e.getData();
        if (data != null) {
            return ResponseEntity.status(e.getCode()).body(
                    new ApiResponse<>(e.getCode(), e.getMessage(), data,
                            System.currentTimeMillis(), null));
        }
        return ResponseEntity.status(e.getCode()).body(ApiResponse.fail(e.getCode(), e.getMessage()));
    }

    /**
     * v2.2.90: HttpRequestMethodNotSupportedException 也兜底返 405 (而不是 500)
     */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(405).body(
                ApiResponse.fail(405, "方法不支持: " + e.getMethod()));
    }

    /**
     * 兜底异常（v2.2.35）：不把原始异常 message 返回前端（防信息泄漏）。
     * 生产环境推荐暴露 traceId 给前端以便用户报障。
     */
    /**
     * 兜底异常（v2.2.35）：不把原始异常 message 返回前端（防信息泄漏）。
     *
     * <p>v2.2.90 重要: Spring 6 的 NoResourceFoundException 继承 ResponseStatusException,
     * {@code DefaultHandlerExceptionResolver} 会优先按 statusCode=500 处理,
     * 导致我们的 {@link #handleNoResource} 永远不会被调用.
     * 这里必须在 handleAny 里再次识别 NoResourceFoundException, 把它转成 404.
     *
     * <p>识别方法: 遍历 cause chain, 看类名包含 NoResourceFoundException 或 NoHandlerFoundException.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleAny(Exception e) {
        // v2.2.90: 路径不存在 → 404 (兼容 Spring 6 NoResourceFoundException 走 statusCode=500)
        Throwable cur = e;
        while (cur != null) {
            String cn = cur.getClass().getName();
            if (cn.contains("NoResourceFoundException") || cn.contains("NoHandlerFoundException")) {
                log.warn("[no resource] path={}", e.getMessage());
                return ResponseEntity.status(404).body(
                        ApiResponse.fail(404, "路径不存在"));
            }
            cur = cur.getCause();
        }
        // HttpRequestMethodNotSupportedException → 405
        if (e instanceof org.springframework.web.HttpRequestMethodNotSupportedException) {
            return ResponseEntity.status(405).body(
                    ApiResponse.fail(405, "方法不支持: " +
                            ((org.springframework.web.HttpRequestMethodNotSupportedException) e).getMethod()));
        }

        String traceId = java.util.UUID.randomUUID().toString().substring(0, 8);
        log.error("[unhandled error] traceId={} msg={}", traceId, e.getMessage(), e);
        return ResponseEntity.status(500).body(
                ApiResponse.fail(500, "服务异常，请稍后重试 (traceId=" + traceId + ")"));
    }
}
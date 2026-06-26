package com.example.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应包装（合规：所有响应统一格式，便于审计）
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String msg;
    private T data;
    private Long ts;
    private String traceId;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, System.currentTimeMillis(), null);
    }
    public static <T> ApiResponse<T> fail(int code, String msg) {
        return new ApiResponse<>(code, msg, null, System.currentTimeMillis(), null);
    }
    public static <T> ApiResponse<T> fail(int code, String msg, String traceId) {
        return new ApiResponse<>(code, msg, null, System.currentTimeMillis(), traceId);
    }
}
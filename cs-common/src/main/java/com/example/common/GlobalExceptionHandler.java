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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleAny(Exception e) {
        log.error("unhandled error", e);
        return ResponseEntity.status(500).body(ApiResponse.fail(500, "服务异常: " + e.getMessage()));
    }
}
package com.example.robot.controller;

import com.example.common.ApiResponse;
import com.example.common.RichMessage;
import com.example.common.SecurityContextHolder;
import com.example.robot.service.RobotEngine;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 机器人对话 API（被 cs-im 内部调用，也可前端直连）
 */
@RestController
@RequestMapping("/robot")
@RequiredArgsConstructor
public class RobotController {

    private final RobotEngine engine;

    @PostMapping("/chat")
    public ApiResponse<RichMessage> chat(@RequestBody ChatReq req) {
        String customerId = req.getCustomerId() != null
                ? req.getCustomerId()
                : SecurityContextHolder.requireUserId();
        return ApiResponse.ok(engine.handle(customerId, req.getText()));
    }

    @GetMapping("/greeting")
    public ApiResponse<RichMessage> greeting() {
        return ApiResponse.ok(engine.greeting());
    }

    @Data
    public static class ChatReq {
        private String customerId;
        private String text;
    }
}
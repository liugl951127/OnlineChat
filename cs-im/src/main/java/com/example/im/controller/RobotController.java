package com.example.im.controller;

import com.example.common.ApiException;
import com.example.common.ApiResponse;
import com.example.common.RichMessage;
import com.example.common.SecurityContextHolder;
import com.example.im.service.RobotEngine;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 机器人对话 REST 接口（v1.9.0）
 *
 * <p>由 cs-im 内部调用，或前端直连调试。
 */
@RestController
@RequestMapping("/robot")
@RequiredArgsConstructor
public class RobotController {

    /** 机器人引擎 */
    private final RobotEngine engine;

    /**
     * 机器人应答（POST /robot/chat）
     */
    @PostMapping("/chat")
    public ApiResponse<RichMessage> chat(@RequestBody ChatReq req) {
        // 1) 取 customerId
        String customerId = req.getCustomerId();
        if (customerId == null || customerId.isBlank()) {
            var ctx = SecurityContextHolder.current();
            if (ctx == null) throw new ApiException(401, "未登录");
            customerId = ctx.getUserId();
        }

        // 2) 引擎处理
        return ApiResponse.ok(engine.handle(customerId, req.getText()));
    }

    /**
     * 欢迎语
     */
    @GetMapping("/greeting")
    public ApiResponse<RichMessage> greeting() {
        return ApiResponse.ok(RichMessage.text(
                "👋 你好，我是智能客服小助手\n" +
                        "我可以帮你：查询账单、推荐产品、风险评估、转接人工。\n" +
                        "请输入你的问题，或回复「人工」转接坐席。"
        ));
    }

    /**
     * 请求体
     */
    @Data
    public static class ChatReq {
        /** 客户 ID（可空，默认取当前登录用户） */
        private String customerId;
        /** 用户输入文本 */
        private String text;
    }
}
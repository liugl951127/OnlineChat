package com.example.im.controller;

import com.example.common.ApiException;
import com.example.common.ApiResponse;
import com.example.common.SecurityContextHolder;
import com.example.im.domain.ChatMessage;
import com.example.im.repo.ChatMessageRepo;
import com.example.im.service.MessageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息 REST 接口（v1.8.0 简化版：发送 + 撤回 + 历史）
 */
@RestController
@RequestMapping("/im/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final ChatMessageRepo messageRepo;

    @PostMapping("/send")
    public ApiResponse<ChatMessage> send(@RequestBody SendReq req) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        ChatMessage m = messageService.send(req.getSessionId(), ctx.getUserId(),
                ctx.getDisplayName() != null ? ctx.getDisplayName() : ctx.getUserId(),
                ctx.getRole(), req.getContent(), req.getType());
        return ApiResponse.ok(m);
    }

    @PostMapping("/{id}/recall")
    public ApiResponse<ChatMessage> recall(@PathVariable Long id) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        messageService.recall(id, ctx.getUserId());
        return ApiResponse.ok(messageRepo.findById(id).orElse(null));
    }

    @GetMapping("/history/{sessionId}")
    public ApiResponse<List<ChatMessage>> history(@PathVariable Long sessionId,
                                                  @RequestParam(defaultValue = "100") int limit) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        return ApiResponse.ok(messageService.history(sessionId, limit));
    }

    @Data
    public static class SendReq {
        private Long sessionId;
        private String content;
        private String type;
    }
}
package com.example.im.controller;

import com.example.common.ApiResponse;
import com.example.common.SecurityContextHolder;
import com.example.im.domain.ChatMessage;
import com.example.im.repo.ChatMessageRepo;
import com.example.im.service.MessageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 消息 REST 接口（含引用/撤回/反应）
 * 鉴权：必须登录；客户只能操作自己的，坐席只能操作自己接听的
 */
@RestController
@RequestMapping("/im/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final ChatMessageRepo messageRepo;

    /** 客户/坐席发消息（带引用） */
    @PostMapping("/send")
    public ApiResponse<ChatMessage> send(@RequestBody SendReq req) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new com.example.common.ApiException(401, "未登录");
        ChatMessage m = messageService.send(req.getSessionId(), ctx.getRole(), ctx.getUserId(),
                req.getContent(), req.getReplyToId());
        return ApiResponse.ok(m);
    }

    /** 撤回消息 */
    @PostMapping("/{id}/recall")
    public ApiResponse<ChatMessage> recall(@PathVariable Long id) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new com.example.common.ApiException(401, "未登录");
        return ApiResponse.ok(messageService.recall(id, ctx.getUserId()));
    }

    /** 反应（点赞/点踩） */
    @PostMapping("/{id}/react")
    public ApiResponse<Map<String, Object>> react(@PathVariable Long id, @RequestBody ReactReq req) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new com.example.common.ApiException(401, "未登录");
        return ApiResponse.ok(messageService.react(id, ctx.getUserId(), ctx.getRole(), req.getEmoji()));
    }

    /** 单条消息详情 */
    @GetMapping("/{id}")
    public ApiResponse<ChatMessage> get(@PathVariable Long id) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new com.example.common.ApiException(401, "未登录");
        ChatMessage m = messageRepo.findById(id)
                .orElseThrow(() -> new com.example.common.ApiException(404, "消息不存在"));
        return ApiResponse.ok(m);
    }

    @Data
    public static class SendReq {
        private Long sessionId;
        private String content;
        private Long replyToId;
    }

    @Data
    public static class ReactReq {
        private String emoji;
    }
}
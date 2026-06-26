package com.example.im.controller;

import com.example.common.ApiException;
import com.example.common.ApiResponse;
import com.example.common.SecurityContextHolder;
import com.example.im.service.VideoReplayService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 视频回溯 REST 接口（v1.9.0）
 *
 * <p>前端播放器调 {@code GET /im/replay/{sessionId}} 拿帧数据。
 */
@RestController
@RequestMapping("/im/replay")
@RequiredArgsConstructor
public class VideoReplayController {

    private final VideoReplayService replayService;

    /** 生成回放数据 */
    @GetMapping("/{sessionId}")
    public ApiResponse<Map<String, Object>> replay(@PathVariable Long sessionId) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        return ApiResponse.ok(replayService.generateReplay(sessionId));
    }

    /** 设置回放视频 URL（管理员） */
    @PostMapping("/{sessionId}/video-url")
    public ApiResponse<Boolean> setVideoUrl(@PathVariable Long sessionId, @RequestBody SetUrlReq req) {
        replayService.setReplayUrl(sessionId, req.getVideoUrl());
        return ApiResponse.ok(true);
    }

    /** 给消息打帧截图 URL */
    @PostMapping("/message/{messageId}/frame-url")
    public ApiResponse<Boolean> setFrameUrl(@PathVariable Long messageId, @RequestBody SetUrlReq req) {
        replayService.setFrameUrl(messageId, req.getVideoUrl());
        return ApiResponse.ok(true);
    }

    @Data
    public static class SetUrlReq {
        private String videoUrl;
    }
}
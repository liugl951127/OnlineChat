package com.example.message.controller;

import com.example.common.ApiResponse;
import com.example.message.service.OfflineMessageStore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 离线消息查询（前端轮询 / 上线拉取）
 */
@RestController
@RequestMapping("/offline")
@RequiredArgsConstructor
public class OfflineMessageController {

    private final OfflineMessageStore store;

    /** 查询指定用户的离线消息（Redis 列表） */
    @GetMapping("/list")
    public ApiResponse<List<?>> list(@RequestParam String userId) {
        return ApiResponse.ok(store.peek(userId, 100));
    }

    /** 查看但不清空 */
    @GetMapping("/peek")
    public ApiResponse<List<?>> peek(@RequestParam String userId,
                                     @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(store.peek(userId, limit));
    }

    /** 拉取并清空（用户上线） */
    @PostMapping("/drain")
    public ApiResponse<List<?>> drain(@RequestBody Map<String, String> req) {
        return ApiResponse.ok(store.drain(req.get("userId")));
    }

    /** 当前未投递消息数 */
    @GetMapping("/size")
    public ApiResponse<Long> size(@RequestParam String userId) {
        return ApiResponse.ok(store.size(userId));
    }
}
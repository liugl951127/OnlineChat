package com.example.message.controller;

import com.example.common.kafka.ChatMessageEvent;
import com.example.message.service.OfflineMessageStore;
import com.example.message.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 消息 REST API（供前端 / 网关调用）
 */
@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
public class MessageController {

    private final OfflineMessageStore offlineStore;
    private final PresenceService presenceService;

    /** 用户上线时拉取所有离线消息 */
    @GetMapping("/offline/{userId}")
    public List<ChatMessageEvent> drain(@PathVariable String userId) {
        return offlineStore.drain(userId);
    }

    /** 用户上线时拉取（不清空） */
    @GetMapping("/offline/{userId}/peek")
    public List<ChatMessageEvent> peek(@PathVariable String userId,
                                       @RequestParam(defaultValue = "50") int limit) {
        return offlineStore.peek(userId, limit);
    }

    /** 离线消息数 */
    @GetMapping("/offline/{userId}/count")
    public Map<String, Long> count(@PathVariable String userId) {
        return Map.of("userId", userId == null ? 0L : 1L, "count", offlineStore.size(userId));
    }

    /** 检查在线状态 */
    @GetMapping("/presence/{userId}")
    public Map<String, Object> presence(@PathVariable String userId) {
        boolean online = presenceService.isOnline(userId);
        return Map.of("userId", userId, "online", online);
    }

    /** 在线人数 */
    @GetMapping("/presence/online-count")
    public Map<String, Long> onlineCount() {
        return Map.of("count", presenceService.onlineCount());
    }
}
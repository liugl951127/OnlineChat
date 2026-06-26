package com.example.message.service;

import com.example.common.kafka.ChatMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 离线消息存储（Redis List）
 *
 * <p>用户离线时收到的消息暂存 Redis，上线后拉取。
 *
 * <p>数据结构：
 * <ul>
 *   <li>Key: {@code offline:msg:{userId}} → List<JSON></li>
 *   <li>最大长度：100 条（避免无限增长）</li>
 *   <li>TTL：7 天（离线太久的消息自动清理）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineMessageStore {

    private final StringRedisTemplate redis;

    @Value("${cs.offline.max-messages:100}")
    private int maxMessages;

    @Value("${cs.offline.ttl-days:7}")
    private int ttlDays;

    /** 离线消息 key */
    public static String key(String userId) {
        return "offline:msg:" + userId;
    }

    /** 存离线消息（LPUSH + LTRIM + EXPIRE） */
    public void push(String userId, ChatMessageEvent event) {
        String json = JsonUtil.toJson(event);
        String k = key(userId);
        redis.opsForList().leftPush(k, json);
        // 限制最大长度
        redis.opsForList().trim(k, 0, maxMessages - 1);
        // 设置过期
        redis.expire(k, Duration.ofDays(ttlDays));
        log.debug("[Offline] stored for user={} msgId={}", userId, event.getMsgId());
    }

    /** 批量推送（同一用户多条） */
    public void pushBatch(String userId, List<ChatMessageEvent> events) {
        if (events == null || events.isEmpty()) return;
        String k = key(userId);
        String[] values = events.stream().map(JsonUtil::toJson).toArray(String[]::new);
        redis.opsForList().leftPushAll(k, values);
        redis.opsForList().trim(k, 0, maxMessages - 1);
        redis.expire(k, Duration.ofDays(ttlDays));
    }

    /** 拉取并清空（用户上线） */
    public List<ChatMessageEvent> drain(String userId) {
        String k = key(userId);
        List<String> jsonList = redis.opsForList().range(k, 0, -1);
        if (jsonList == null || jsonList.isEmpty()) return Collections.emptyList();
        // 立刻删除（避免重复消费）
        redis.delete(k);
        List<ChatMessageEvent> events = new ArrayList<>(jsonList.size());
        for (String json : jsonList) {
            try {
                events.add(JsonUtil.fromJson(json, ChatMessageEvent.class));
            } catch (Exception e) {
                log.warn("[Offline] parse error: {}", json, e);
            }
        }
        log.info("[Offline] drained {} messages for user={}", events.size(), userId);
        return events;
    }

    /** 查看（不清空） */
    public List<ChatMessageEvent> peek(String userId, int limit) {
        String k = key(userId);
        List<String> jsonList = redis.opsForList().range(k, 0, limit - 1);
        if (jsonList == null || jsonList.isEmpty()) return Collections.emptyList();
        List<ChatMessageEvent> events = new ArrayList<>(jsonList.size());
        for (String json : jsonList) {
            try {
                events.add(JsonUtil.fromJson(json, ChatMessageEvent.class));
            } catch (Exception e) {
                log.warn("[Offline] parse error: {}", json, e);
            }
        }
        return events;
    }

    /** 当前离线消息数 */
    public long size(String userId) {
        Long size = redis.opsForList().size(key(userId));
        return size == null ? 0 : size;
    }
}
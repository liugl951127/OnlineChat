package com.example.message.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 用户在线状态服务（基于 Redis）
 *
 * <p>WebSocket 连接建立时 → ONLINE
 * <br>WebSocket 断开时 → OFFLINE
 * <br>心跳每 30s 续期（EXPIRE）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private final StringRedisTemplate redis;

    /** 状态 key：presence:user:{userId} → connectionId */
    public static String userKey(String userId) {
        return "presence:user:" + userId;
    }

    /** 连接 key：presence:conn:{connId} → userId（反查） */
    public static String connKey(String connId) {
        return "presence:conn:" + connId;
    }

    /** 标记用户在线（设置 connectionId + TTL 60s） */
    public void online(String userId, String connectionId, String serviceInstance) {
        redis.opsForValue().set(userKey(userId), connectionId + "|" + serviceInstance, Duration.ofSeconds(60));
        redis.opsForValue().set(connKey(connectionId), userId, Duration.ofSeconds(60));
    }

    /** 心跳续期 */
    public void heartbeat(String userId, String connectionId, String serviceInstance) {
        online(userId, connectionId, serviceInstance);
    }

    /** 标记离线（连接断开） */
    public void offline(String connectionId) {
        String userId = redis.opsForValue().get(connKey(connectionId));
        if (userId != null) {
            redis.delete(userKey(userId));
            redis.delete(connKey(connectionId));
            log.info("[Presence] user {} OFFLINE via conn {}", userId, connectionId);
        }
    }

    /** 检查用户是否在线 */
    public boolean isOnline(String userId) {
        Boolean exists = redis.hasKey(userKey(userId));
        return Boolean.TRUE.equals(exists);
    }

    /** 批量检查在线状态 */
    public Map<String, Boolean> batchIsOnline(Set<String> userIds) {
        Map<String, Boolean> result = new HashMap<>();
        for (String id : userIds) {
            result.put(id, isOnline(id));
        }
        return result;
    }

    /** 获取在线用户总数 */
    public long onlineCount() {
        Set<String> keys = redis.keys("presence:user:*");
        return keys == null ? 0 : keys.size();
    }
}
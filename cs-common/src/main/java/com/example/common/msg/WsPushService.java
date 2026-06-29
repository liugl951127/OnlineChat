package com.example.common.msg;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v2.3.0 跨实例 WS 推送服务 - Pub/Sub + 离线兜底
 *
 * <p>推送策略:
 * <ol>
 *   <li>cs-common 不知道 WebSocket (避免 ws 依赖污染)
 *   <li>push 时: Redis Pub/Sub {@code ws:push:{userId}} 广播 → 所有实例收到
 *   <li>cs-im 等服务自己实现 listener, 用 SimpMessagingTemplate 推 STOMP
 *   <li>本实例没有 user 在线 → 落离线 (SETNX 防重复)
 * </ol>
 *
 * <p>为什么不直接 WebSocket 点对点:
 * <ul>
 *   <li>Spring Boot WebSocket session 不跨实例 (sticky session 需要 LB 配置)</li>
 *   <li>Redis Pub/Sub 天然跨实例广播</li>
 *   <li>Kafka 太重, Pub/Sub 毫秒级</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsPushService {

    private final StringRedisTemplate redis;
    private final OfflineMessageStore offlineStore;

    /** 仅记 user 在线 (不依赖 session 对象) */
    private final Set<String> onlineUserIds = ConcurrentHashMap.newKeySet();

    /** 当前实例 ID (随机, 防 Pub/Sub 回环) */
    private final String instanceId = java.util.UUID.randomUUID().toString().substring(0, 8);

    private static final String CHANNEL_PREFIX = "ws:push:";

    @PostConstruct
    public void init() {
        log.info("[WsPush] init instanceId={} channels=ws:push:*", instanceId);
    }

    /**
     * 注册 user 在线 (服务握手成功时调)
     */
    public void register(String userId) {
        onlineUserIds.add(userId);
        log.info("[WsPush] register user={} online={}", userId, onlineUserIds.size());
    }

    /**
     * 注销 (用户断线时调)
     */
    public void unregister(String userId) {
        boolean removed = onlineUserIds.remove(userId);
        if (removed) {
            log.info("[WsPush] unregister user={} remaining={}", userId, onlineUserIds.size());
        }
    }

    /**
     * 当前实例是否在线
     */
    public boolean isLocalOnline(String userId) {
        return onlineUserIds.contains(userId);
    }

    /**
     * 推一条消息 (跨实例广播)
     *
     * @param userId      目标用户
     * @param msgId       消息唯一 ID (离线去重)
     * @param jsonPayload JSON 字符串
     */
    public void push(String userId, String msgId, String jsonPayload) {
        String channel = CHANNEL_PREFIX + userId;
        try {
            redis.convertAndSend(channel, msgId + "|" + jsonPayload);
        } catch (Exception e) {
            log.warn("[WsPush] pubsub send 失败, 落离线 user={} err={}", userId, e.toString());
            offlineStore.push(userId, msgId, jsonPayload);
        }
    }

    /**
     * Pub/Sub 收到广播 → 本实例有 user 在线就 deliver, 否则落离线
     *
     * <p>deliver 由 cs-im 等服务注入 (用 SimpMessagingTemplate.convertAndSendToUser)
     */
    private java.util.function.BiConsumer<String, String> localDeliver;

    public void setLocalDeliver(java.util.function.BiConsumer<String, String> deliver) {
        this.localDeliver = deliver;
        log.info("[WsPush] localDeliver 注册成功 (cs-im 等服务注入)");
    }

    public void onBroadcastReceived(String userId, String msgId, String jsonPayload) {
        if (onlineUserIds.contains(userId)) {
            try {
                if (localDeliver != null) {
                    localDeliver.accept(userId, jsonPayload);
                }
            } catch (Exception ex) {
                log.warn("[WsPush] deliver 失败, 落离线 user={} err={}", userId, ex.toString());
                pushOrStoreOffline(userId, msgId, jsonPayload);
            }
        } else {
            pushOrStoreOffline(userId, msgId, jsonPayload);
        }
    }

    /**
     * 兜底落离线 (SETNX 防重复)
     */
    private void pushOrStoreOffline(String userId, String msgId, String jsonPayload) {
        try {
            String dedupKey = "ws:push:dedup:" + msgId;
            Boolean firstTime = redis.opsForValue().setIfAbsent(dedupKey, "1", Duration.ofMinutes(5));
            if (Boolean.TRUE.equals(firstTime)) {
                offlineStore.push(userId, msgId, jsonPayload);
            }
        } catch (Exception ex) {
            offlineStore.push(userId, msgId, jsonPayload);
        }
    }
}
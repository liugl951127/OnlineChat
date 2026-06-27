package com.example.im.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 离线消息存储（v1.9.0）
 *
 * <p>真实业务场景：
 * <ul>
 *   <li>客户发消息但无坐席在线 → 暂存到 Redis</li>
 *   <li>客户聊天页关闭后再回来 → 拉取离线消息</li>
 *   <li>Redis 用 List（LPUSH + LTRIM 限制长度）+ TTL 7 天</li>
 * </ul>
 *
 * <p>与 {@link OfflinePushService} 区别：
 * <ul>
 *   <li>OfflineMessageStore：暂存文本消息（Redis）</li>
 *   <li>OfflinePushService：通过企业微信 / 公众号推送通知</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineMessageStore {

    /** Redis 客户端 */
    private final StringRedisTemplate redis;

    /** JSON 序列化 */
    private final ObjectMapper json = new ObjectMapper();

    /** 每个会话最多保留的离线消息条数 */
    private static final int MAX_PER_SESSION = 100;

    /** Redis key 前缀 */
    private static final String KEY_PREFIX = "offline:msg:session:";

    /** 消息 TTL（7 天） */
    private static final Duration TTL = Duration.ofDays(7);

    /**
     * 保存离线消息
     *
     * @param sessionId 会话 ID
     * @param fromId    发送方 ID
     * @param fromRole  发送方角色
     * @param content   消息内容
     */
    public void save(Long sessionId, String fromId, String fromRole, String content) {
        try {
            // 1) 构造消息 JSON
            Map<String, Object> msg = Map.of(
                    "fromId", fromId,
                    "fromRole", fromRole,
                    "content", content,
                    "ts", System.currentTimeMillis()
            );
            String payload = json.writeValueAsString(msg);

            // 2) Redis LPUSH + LTRIM（限制最大条数）
            String key = KEY_PREFIX + sessionId;
            redis.opsForList().leftPush(key, payload);
            redis.opsForList().trim(key, 0, MAX_PER_SESSION - 1);

            // 3) 设置 TTL（每次写入都刷新）
            redis.expire(key, TTL);
        } catch (Exception e) {
            log.warn("[Offline] save failed: {}", e.getMessage());
        }
    }

    /**
     * 拉取并清空离线消息
     *
     * @param sessionId 会话 ID
     * @return 离线消息列表（按时间升序）
     */
    public List<Map<String, Object>> drain(Long sessionId) {
        try {
            String key = KEY_PREFIX + sessionId;
            // 弹出全部
            List<String> raw = redis.opsForList().range(key, 0, -1);
            // 清空
            redis.delete(key);
            if (raw == null) return Collections.emptyList();

            // 反向 + 解析（LPUSH 后 LRANGE 是倒序）
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = raw.size() - 1; i >= 0; i--) {
                try {
                    // 使用 TypeReference 指定泛型类型，彻底避免 unchecked 警告
                    Map<String, Object> msg = json.readValue(raw.get(i), new TypeReference<Map<String, Object>>() {});
                    result.add(msg);
                } catch (Exception ignored) {}
            }
            return result;
        } catch (Exception e) {
            log.warn("[Offline] drain failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查看离线消息数量（不清空）
     */
    public long size(Long sessionId) {
        try {
            String key = KEY_PREFIX + sessionId;
            Long s = redis.opsForList().size(key);
            return s != null ? s : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
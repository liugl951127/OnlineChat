package com.example.common.msg;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * v2.3.0 离线消息双通道 - 落盘端
 *
 * <p>在线 → Redis Pub/Sub 即时推送
 * <br>离线 → Redis List (LPUSH) 落盘, 客户端进站时 drain
 *
 * <p>Key: {@code offline:msg:{userId}} -- List<msgId>
 * <br>每个 msgId 对应 hash {@code offline:msg:detail:{msgId}} 存消息体
 * <br>TTL: 24h (可配置)
 *
 * <p>为什么用 LPUSH 不直接存全文: List 省内存, 详情用单独 Hash 方便单独 TTL
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfflineMessageStore {

    private final StringRedisTemplate redis;

    private static final String LIST_PREFIX = "offline:msg:";
    private static final String DETAIL_PREFIX = "offline:msg:detail:";
    private static final Duration TTL = Duration.ofHours(24);
    private static final int MAX_PER_USER = 200; // 单用户上限

    /**
     * 写入离线消息
     */
    public void push(String userId, String msgId, String jsonPayload) {
        try {
            String listKey = LIST_PREFIX + userId;
            String detailKey = DETAIL_PREFIX + msgId;

            // 详情存 Hash + 24h TTL
            redis.opsForValue().set(detailKey, jsonPayload, TTL);

            // LPUSH 到用户列表 + LTRIM 截断到 MAX
            redis.opsForList().leftPush(listKey, msgId);
            redis.opsForList().trim(listKey, 0, MAX_PER_USER - 1);
            redis.expire(listKey, TTL);
        } catch (Exception e) {
            log.warn("[Offline] push 失败 user={} msgId={} err={}", userId, msgId, e.toString());
        }
    }

    /**
     * 用户进站 → drain 全部离线消息, 删除已读
     */
    public List<String> drain(String userId) {
        try {
            String listKey = LIST_PREFIX + userId;
            // 弹出全部 (顺序 = LPUSH 后从右到左 = 时间正序)
            List<String> msgIds = redis.opsForList().range(listKey, 0, -1);
            if (msgIds == null || msgIds.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> payloads = new ArrayList<>(msgIds.size());
            for (String msgId : msgIds) {
                String detailKey = DETAIL_PREFIX + msgId;
                String payload = redis.opsForValue().get(detailKey);
                if (payload != null) {
                    payloads.add(payload);
                }
                // 删详情
                redis.delete(detailKey);
            }
            // 删 list
            redis.delete(listKey);
            log.info("[Offline] drain user={} count={}", userId, payloads.size());
            return payloads;
        } catch (Exception e) {
            log.warn("[Offline] drain 失败 user={} err={}", userId, e.toString());
            return Collections.emptyList();
        }
    }

    /**
     * 查离线消息数量 (UI 提示用)
     */
    public long count(String userId) {
        try {
            Long size = redis.opsForList().size(LIST_PREFIX + userId);
            return size == null ? 0 : size;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 清空某用户离线 (强制登出等场景)
     */
    public void clear(String userId) {
        try {
            String listKey = LIST_PREFIX + userId;
            List<String> msgIds = redis.opsForList().range(listKey, 0, -1);
            if (msgIds != null) {
                msgIds.forEach(id -> redis.delete(DETAIL_PREFIX + id));
            }
            redis.delete(listKey);
        } catch (Exception e) {
            log.warn("[Offline] clear user={} err={}", userId, e.toString());
        }
    }
}
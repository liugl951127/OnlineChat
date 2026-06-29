package com.example.common.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * v2.3.0 Redis Lua 原子限流器 (单 RTT, 毫秒级)
 *
 * <p>Lua 脚本: INCR + EXPIRE (原子, 防并发穿透)
 * <p>用法:
 * <pre>
 *   if (!rateLimiter.tryAcquire("login:" + ip, 5, 60)) {
 *       throw ApiException(429, "尝试次数过多");
 *   }
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    private final StringRedisTemplate redis;

    /**
     * 滑动窗口 (INCR + EXPIRE)
     * 返回当前计数, 超过 limit 拒绝
     */
    private static final String LUA_INCR_EXPIRE =
        "local n = redis.call('INCR', KEYS[1]) " +
        "if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
        "return n";

    private final DefaultRedisScript<Long> incrScript = new DefaultRedisScript<>(LUA_INCR_EXPIRE, Long.class);

    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        try {
            Long n = redis.execute(incrScript, List.of(key), String.valueOf(windowSeconds));
            return n != null && n <= limit;
        } catch (Exception e) {
            // Redis 故障 → 不限流 (业务优先)
            log.warn("[RateLimit] Redis 故障, 放行: {}", e.toString());
            return true;
        }
    }

    /**
     * 当前计数 (UI 显示用)
     */
    public long currentCount(String key) {
        String val = redis.opsForValue().get(key);
        return val == null ? 0 : Long.parseLong(val);
    }

    /**
     * 重置 (登录成功后调用)
     */
    public void reset(String key) {
        redis.delete(key);
    }
}
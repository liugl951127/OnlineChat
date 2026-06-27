package com.example.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * JWT Token 黑名单（v2.2.35）。
 *
 * <p>提供"主动失效 token"能力：
 * <ul>
 *   <li>登出 → blacklist 用户的当前 token</li>
 *   <li>踢人（封号） → blacklist 该用户所有 token</li>
 *   <li>强制改密后失效旧 token</li>
 * </ul>
 *
 * <p>key 设计：
 * <ul>
 *   <li>blacklist:token:{tokenHash}  → "1" (TTL = token 剩余有效期)</li>
 *   <li>blacklist:user:{userId}      → "v2" (version, 踢人时 +1 让旧 token 失效)</li>
 * </ul>
 *
 * <p>未来可扩展：双 token (access + refresh)、设备绑定、IP 绑定等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redis;

    /** 失效单个 token */
    public void revoke(String token, long ttlSeconds) {
        if (token == null || token.isBlank()) return;
        // 用 token hash 作为 key（避免超长 key）
        String hash = Integer.toHexString(token.hashCode());
        redis.opsForValue().set("blacklist:token:" + hash, "1",
                Duration.ofSeconds(Math.max(1, ttlSeconds)));
        log.info("[Blacklist] revoked token hash={} ttl={}s", hash, ttlSeconds);
    }

    /** 检查 token 是否在黑名单 */
    public boolean isRevoked(String token) {
        if (token == null || token.isBlank()) return false;
        String hash = Integer.toHexString(token.hashCode());
        return Boolean.TRUE.equals(redis.hasKey("blacklist:token:" + hash));
    }

    /** 踢人：让该用户所有旧 token 失效（增加 user version） */
    public Long bumpUserVersion(String userId) {
        Long v = redis.opsForValue().increment("blacklist:user:" + userId);
        redis.expire("blacklist:user:" + userId, Duration.ofDays(30));
        log.info("[Blacklist] bumped user={} to version={}", userId, v);
        return v;
    }

    /** 检查 user version 是否匹配（踢人后旧 token 的 version 落后） */
    public Long getUserVersion(String userId) {
        String v = redis.opsForValue().get("blacklist:user:" + userId);
        return v == null ? 0L : Long.parseLong(v);
    }
}
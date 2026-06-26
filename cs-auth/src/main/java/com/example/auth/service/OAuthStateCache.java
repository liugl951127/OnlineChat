package com.example.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * OAuth state 管理（防 CSRF）
 * <p>生成 state 时存入 Redis，callback 时校验并删除（一次性）
 */
@Service
public class OAuthStateCache {

    private final StringRedisTemplate redis;

    public OAuthStateCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String generate(String provider) {
        String state = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set("oauth:state:" + provider + ":" + state, "1", Duration.ofMinutes(10));
        return state;
    }

    public boolean verifyAndConsume(String provider, String state) {
        if (state == null || state.isBlank()) return false;
        Boolean exists = redis.delete("oauth:state:" + provider + ":" + state);
        return Boolean.TRUE.equals(exists);
    }
}
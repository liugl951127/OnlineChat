package com.example.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TokenBlacklistService v2.2.35 单元测试（用 mock Redis）。
 */
class TokenBlacklistServiceTest {

    @Test
    void revoke_marks_token_as_blacklisted() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        TokenBlacklistService svc = new TokenBlacklistService(redis);
        String token = "eyJhbGciOiJIUzM4NCJ9.payload.signature";
        svc.revoke(token, 3600);

        // 验证 set 被调用
        verify(ops).set(eq("blacklist:token:" + Integer.toHexString(token.hashCode())),
                eq("1"), any(Duration.class));
    }

    @Test
    void isRevoked_returns_true_if_blacklisted() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        Map<String, String> storage = new HashMap<>();
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(inv -> storage.get(inv.getArgument(0)));
        when(redis.hasKey(anyString())).thenAnswer(inv -> storage.containsKey(inv.getArgument(0)));

        TokenBlacklistService svc = new TokenBlacklistService(redis);
        String token = "eyJ.aaa.bbb";

        assertFalse(svc.isRevoked(token));
        svc.revoke(token, 3600);
        storage.put("blacklist:token:" + Integer.toHexString(token.hashCode()), "1");
        assertTrue(svc.isRevoked(token));
    }

    @Test
    void bumpUserVersion_returns_incremented_version() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        AtomicLong counter = new AtomicLong(0);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenAnswer(inv -> counter.incrementAndGet());

        TokenBlacklistService svc = new TokenBlacklistService(redis);
        Long v1 = svc.bumpUserVersion("c-user1");
        Long v2 = svc.bumpUserVersion("c-user1");
        Long v3 = svc.bumpUserVersion("c-user1");
        assertEquals(1L, v1);
        assertEquals(2L, v2);
        assertEquals(3L, v3);
    }

    @Test
    void null_token_revoked_silently() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        TokenBlacklistService svc = new TokenBlacklistService(redis);

        // 不应该抛异常
        assertDoesNotThrow(() -> svc.revoke(null, 3600));
        assertDoesNotThrow(() -> svc.revoke("", 3600));
        assertFalse(svc.isRevoked(null));
    }
}
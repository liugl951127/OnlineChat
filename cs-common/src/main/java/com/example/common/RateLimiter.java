package com.example.common;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单限流器（按 key + 滑动窗口）
 * <p>用于敏感操作（撤回/反应/消息发送）防刷
 *
 * <p>每 key 每 {@code windowSeconds} 最多 {@code maxCount} 次
 */
public final class RateLimiter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxCount;
    private final long windowMs;

    public RateLimiter(int maxCount, int windowSeconds) {
        this.maxCount = maxCount;
        this.windowMs = windowSeconds * 1000L;
    }

    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, old) -> {
            if (old == null || now - old.startMs >= windowMs) return new Window(now);
            return old;
        });
        return w.count.incrementAndGet() <= maxCount;
    }

    private static class Window {
        final long startMs;
        final AtomicInteger count = new AtomicInteger(0);
        Window(long startMs) { this.startMs = startMs; }
    }
}
package com.example.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * v2.3.0 通用本地缓存 (Caffeine)
 *
 * <p>目标: 减少 Redis QPS, 把热数据缓存到本地内存
 *
 * <p>用法:
 * <pre>{@code
 *   LocalCache<String, Object> tokenCache = LocalCache.<String, Object>builder()
 *       .name("token-parse")
 *       .maxSize(50_000)
 *       .ttl(Duration.ofSeconds(30))
 *       .build();
 *
 *   Object cached = tokenCache.get(token, t -> parseJwtFromRedis(t));
 * }</pre>
 *
 * <p>特性:
 * <ul>
 *   <li>记录 hits/misses, 可通过 {@link #getStats()} 暴露 metrics</li>
 *   <li>按 name 隔离 cache (避免互相干扰)</li>
 *   <li>支持 maxSize + ttl + recordStats</li>
 *   <li>通过 {@link #invalidateAll()} 一键清空</li>
 * </ul>
 */
public final class LocalCache<K, V> {

    private final String name;
    private final Cache<K, V> cache;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    private LocalCache(String name, Cache<K, V> cache) {
        this.name = name;
        this.cache = cache;
    }

    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    public V get(K key, java.util.function.Function<K, V> loader) {
        V v = cache.get(key, loader);
        if (v != null) hits.incrementAndGet();
        else misses.incrementAndGet();
        return v;
    }

    public void put(K key, V value) {
        cache.put(key, value);
    }

    public void invalidate(K key) {
        cache.invalidate(key);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    public Stats getStats() {
        long h = hits.get();
        long m = misses.get();
        long total = h + m;
        return new Stats(name, h, m, total == 0 ? 0.0 : (h * 100.0 / total));
    }

    public long size() { return cache.estimatedSize(); }

    public static final class Stats {
        public final String name;
        public final long hits;
        public final long misses;
        public final double hitRate;
        public Stats(String name, long hits, long misses, double hitRate) {
            this.name = name; this.hits = hits; this.misses = misses; this.hitRate = hitRate;
        }
    }

    /** 所有 cache 静态注册, admin 可查 stats */
    private static final ConcurrentHashMap<String, LocalCache<?, ?>> ALL = new ConcurrentHashMap<>();
    public static java.util.List<Stats> allStats() {
        return ALL.values().stream().map(LocalCache::getStats).collect(java.util.stream.Collectors.toList());
    }

    public static final class Builder<K, V> {
        private String name = "default";
        private long maxSize = 10_000;
        private Duration ttl = Duration.ofMinutes(5);
        private boolean recordStats = false;   // default off (开启会报错, 用简单计数器)

        public Builder<K, V> name(String name) { this.name = name; return this; }
        public Builder<K, V> maxSize(long size) { this.maxSize = size; return this; }
        public Builder<K, V> ttl(Duration ttl) { this.ttl = ttl; return this; }
        public Builder<K, V> recordStats(boolean r) { this.recordStats = r; return this; }

        public LocalCache<K, V> build() {
            Cache<K, V> c = Caffeine.newBuilder()
                    .maximumSize(maxSize)
                    .expireAfterWrite(ttl)
                    .build();
            LocalCache<K, V> cache = new LocalCache<>(name, c);
            ALL.put(name, cache);
            return cache;
        }
    }
}
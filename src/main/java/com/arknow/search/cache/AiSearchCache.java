package com.arknow.search.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * Two-level cache for AI search results.
 * <p>
 * L1: Caffeine in-memory (1000 entries, 10-min TTL, async write-back)
 * L2: Redis distributed (1-hour TTL, shared across instances)
 * <p>
 * Cache key = SHA-256(query) — fixed 64 chars, handles CJK safely.
 * Cache value = serialized JSON of answer + articles.
 */
@Component
public class AiSearchCache {
    private static final String REDIS_PREFIX = "ai:search:";

    private final Cache<String, String> l1Cache;
    private final StringRedisTemplate redis;

    public AiSearchCache(StringRedisTemplate redis) {
        this.redis = redis;
        this.l1Cache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .recordStats()
            .build();
    }

    public String get(String query) {
        String key = hash(query);
        // L1: Caffeine
        String cached = l1Cache.getIfPresent(key);
        if (cached != null) return cached;
        // L2: Redis
        cached = redis.opsForValue().get(REDIS_PREFIX + key);
        if (cached != null) {
            l1Cache.put(key, cached); // promote to L1
            return cached;
        }
        return null;
    }

    public void put(String query, String value) {
        String key = hash(query);
        l1Cache.put(key, value);
        redis.opsForValue().set(REDIS_PREFIX + key, value, Duration.ofHours(1));
    }

    public long l1HitCount() { return l1Cache.stats().hitCount(); }
    public long l1MissCount() { return l1Cache.stats().missCount(); }
    public double l1HitRate() { return l1Cache.stats().hitRate(); }

    private static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}

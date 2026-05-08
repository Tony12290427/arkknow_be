package com.arknow.search.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis token-bucket rate limiter for AI search.
 * <p>
 * Capacity: 10 requests per minute per IP.
 * Uses Redis Lua script for atomic token check + refill.
 */
@Component
public class AiSearchRateLimiter {
    private final StringRedisTemplate redis;

    private static final String TOKEN_BUCKET_LUA = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local now = redis.call('TIME')[1]
            local last = redis.call('HGET', key, 'last')
            local tokens = redis.call('HGET', key, 'tokens')
            if not last then last = now; tokens = capacity end
            local elapsed = tonumber(now) - tonumber(last)
            local add = elapsed * rate
            tokens = math.min(capacity, tonumber(tokens) + add)
            if tokens < 1 then
                redis.call('HSET', key, 'last', now)
                redis.call('HSET', key, 'tokens', tokens)
                return 0
            end
            tokens = tokens - 1
            redis.call('HSET', key, 'last', now)
            redis.call('HSET', key, 'tokens', tokens)
            redis.call('PEXPIRE', key, 120000)
            return 1
            """;

    public AiSearchRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Returns true if the request is allowed, false if rate limited. */
    public boolean tryAcquire(String ip) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(TOKEN_BUCKET_LUA, Long.class);
        Long result = redis.execute(script, List.of("rl:ai-search:" + ip), "10", "0.166");
        return result != null && result == 1L;
    }
}

package com.arknow.auth.token;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {
    private final StringRedisTemplate redisTemplate;

    public RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void storeToken(long userId, String tokenId, Duration ttl) {
        redisTemplate.opsForValue().set(key(userId, tokenId), "1", ttl);
    }

    @Override
    public boolean isTokenValid(long userId, String tokenId) {
        return Objects.equals("1", redisTemplate.opsForValue().get(key(userId, tokenId)));
    }

    @Override
    public void revokeToken(long userId, String tokenId) {
        redisTemplate.delete(key(userId, tokenId));
    }

    @Override
    public void revokeAll(long userId) {
        var keys = redisTemplate.keys("auth:rt:%d:*".formatted(userId));
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private static String key(long userId, String tokenId) {
        return "auth:rt:%d:%s".formatted(userId, tokenId);
    }
}

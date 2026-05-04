package com.arknow.auth.token;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * Redis-backed refresh token whitelist.
 * <p>
 * Refresh tokens are issued as stateless JWTs, but their validity is governed by a
 * Redis whitelist. A token is only considered valid if:
 * <ol>
 *   <li>The JWT signature passes verification (done in {@link JwtService})</li>
 *   <li>The JWT is not expired</li>
 *   <li>Its jti exists in this Redis store (i.e. it has not been revoked)</li>
 * </ol>
 * <p>
 * This gives us the best of both worlds: stateless access tokens for performance,
 * stateful refresh tokens for security control (logout, forced re-login, etc.).
 * <p>
 * Key pattern: {@code auth:rt:<userId>:<tokenId>}
 */
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

    /** Returns true only if the exact key exists with value "1". */
    @Override
    public boolean isTokenValid(long userId, String tokenId) {
        return Objects.equals("1", redisTemplate.opsForValue().get(key(userId, tokenId)));
    }

    @Override
    public void revokeToken(long userId, String tokenId) {
        redisTemplate.delete(key(userId, tokenId));
    }

    /** Revokes all refresh tokens for a user, forcing re-login on all devices. */
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

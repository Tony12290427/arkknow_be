package com.arknow.auth.verification;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Redis Hash-backed verification code store.
 * <p>
 * Each code session is a single Redis Hash key with three fields:
 * <ul>
 *   <li>{@code code} — the verification code string</li>
 *   <li>{@code maxAttempts} — allowed wrong attempts</li>
 *   <li>{@code attempts} — how many times verification has been tried</li>
 * </ul>
 * <p>
 * Using a Hash instead of three separate String keys keeps the three related values
 * atomically grouped: they share one TTL, are deleted together, and cannot drift apart.
 * <p>
 * Key pattern: {@code auth:code:<scene>:<identifier>}
 */
@Component
public class RedisVerificationCodeStore implements VerificationCodeStore {
    private static final String FIELD_CODE = "code";
    private static final String FIELD_MAX_ATTEMPTS = "maxAttempts";
    private static final String FIELD_ATTEMPTS = "attempts";

    private final StringRedisTemplate redis;

    public RedisVerificationCodeStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void saveCode(String scene, String identifier, String code, Duration ttl, int maxAttempts) {
        String key = buildKey(scene, identifier);
        HashOperations<String, String, String> ops = redis.opsForHash();
        try {
            ops.put(key, FIELD_CODE, code);
            ops.put(key, FIELD_MAX_ATTEMPTS, String.valueOf(maxAttempts));
            ops.put(key, FIELD_ATTEMPTS, "0");
            redis.expire(key, ttl);
        } catch (DataAccessException ex) {
            throw new RedisSystemException("Failed to save verification code", ex);
        }
    }

    /**
     * Verify the submitted code.
     * <p>
     * On success the entire key is deleted so the code acts as a one-time token.
     * When attempts reach the limit, the TTL is extended to 30 minutes as a penalty
     * to slow down brute-force attacks.
     */
    @Override
    public VerificationCheckResult verify(String scene, String identifier, String code) {
        String key = buildKey(scene, identifier);
        HashOperations<String, String, String> ops = redis.opsForHash();
        Map<String, String> data = ops.entries(key);
        if (data.isEmpty()) {
            return new VerificationCheckResult(VerificationCodeStatus.NOT_FOUND, 0, 0);
        }
        String storedCode = data.get(FIELD_CODE);
        int maxAttempts = parseInt(data.get(FIELD_MAX_ATTEMPTS), 5);
        int attempts = parseInt(data.get(FIELD_ATTEMPTS), 0);

        if (attempts >= maxAttempts) {
            return new VerificationCheckResult(VerificationCodeStatus.TOO_MANY_ATTEMPTS, attempts, maxAttempts);
        }
        if (Objects.equals(storedCode, code)) {
            redis.delete(key);
            return new VerificationCheckResult(VerificationCodeStatus.SUCCESS, attempts, maxAttempts);
        }

        int updated = attempts + 1;
        ops.put(key, FIELD_ATTEMPTS, String.valueOf(updated));
        if (updated >= maxAttempts) {
            redis.expire(key, Duration.ofMinutes(30));
            return new VerificationCheckResult(VerificationCodeStatus.TOO_MANY_ATTEMPTS, updated, maxAttempts);
        }
        return new VerificationCheckResult(VerificationCodeStatus.MISMATCH, updated, maxAttempts);
    }

    @Override
    public void invalidate(String scene, String identifier) {
        redis.delete(buildKey(scene, identifier));
    }

    private static String buildKey(String scene, String identifier) {
        return "auth:code:%s:%s".formatted(scene, identifier);
    }

    /** Parses an integer from a Redis string field, falling back to a default on failure. */
    private static int parseInt(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}

package com.arknow.auth.verification;

import com.arknow.auth.config.AuthProperties;
import com.arknow.common.exception.BusinessException;
import com.arknow.common.exception.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Orchestrates verification code generation, storage, rate limiting, and delivery.
 * <p>
 * Rate limiting operates on two dimensions:
 * <ol>
 *   <li><b>Send interval</b> — how soon the same identifier can request another code.
 *       Implemented via {@code SET key 1 EX N} — the key's TTL is the interval.</li>
 *   <li><b>Daily limit</b> — maximum codes per identifier per calendar day.
 *       Implemented via {@code INCR} on a date-suffixed key, with TTL set only on first increment
 *       to prevent clock-extension attacks.</li>
 * </ol>
 * <p>
 * The generated code uses {@link SecureRandom} rather than {@link java.util.Random}
 * because verification codes are security-sensitive and must be cryptographically unpredictable.
 */
@Service
public class VerificationService {
    /** Cryptographically secure RNG — must not be replaced with java.util.Random. */
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Date suffix for daily limit keys: {@code yyyyMMdd}. */
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final VerificationCodeStore codeStore;
    private final CodeSender codeSender;
    private final StringRedisTemplate stringRedisTemplate;
    private final AuthProperties properties;

    public VerificationService(VerificationCodeStore codeStore, CodeSender codeSender,
                                StringRedisTemplate stringRedisTemplate, AuthProperties properties) {
        this.codeStore = codeStore;
        this.codeSender = codeSender;
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    /**
     * Generates and delivers a verification code for the given scene and identifier.
     * <p>
     * Execution order matters: rate limiting is checked first, then the code is persisted,
     * and only then is delivery attempted. This ensures "register first, send second" —
     * if delivery fails, the code is already stored and can be resent.
     *
     * @param scene      the business context
     * @param identifier normalized phone or email
     * @return result with the identifier, scene, and expiry seconds
     * @throws BusinessException if rate limit or daily quota is exceeded
     */
    public SendCodeResult sendCode(VerificationScene scene, String identifier) {
        // Fail-fast: reject before doing any work
        if (scene == null || !StringUtils.hasText(identifier)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请提供正确的验证码发送参数");
        }
        AuthProperties.Verification cfg = properties.getVerification();
        // Rate-limit checks come before code generation to avoid burning CPU on rejected requests
        enforceSendInterval(scene, identifier, cfg.getSendInterval());
        enforceDailyLimit(scene, identifier, cfg.getDailyLimit());

        String code = generateNumericCode(cfg.getCodeLength());
        codeStore.saveCode(scene.name(), identifier, code, cfg.getTtl(), cfg.getMaxAttempts());
        codeSender.sendCode(scene, identifier, code, (int) cfg.getTtl().toMinutes());
        return new SendCodeResult(identifier, scene, (int) cfg.getTtl().toSeconds());
    }

    /**
     * Validates a submitted code without consuming it on failure.
     *
     * @return a result object indicating success or the specific failure reason
     */
    public VerificationCheckResult verify(VerificationScene scene, String identifier, String code) {
        if (scene == null || !StringUtils.hasText(identifier) || !StringUtils.hasText(code)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码校验参数不完整");
        }
        return codeStore.verify(scene.name(), identifier, code);
    }

    /** Removes a stored code, e.g. after a successful operation. */
    public void invalidate(VerificationScene scene, String identifier) {
        codeStore.invalidate(scene.name(), identifier);
    }

    /**
     * Enforces a minimum interval between successive send requests for the same identifier.
     * Uses Redis {@code SET key 1 EX N} — the simplest possible rate limiter.
     */
    private void enforceSendInterval(VerificationScene scene, String identifier, Duration interval) {
        if (interval.isZero() || interval.isNegative()) return;
        String key = "auth:code:last:" + scene.name() + ":" + identifier;
        String existing = stringRedisTemplate.opsForValue().get(key);
        if (existing != null) {
            throw new BusinessException(ErrorCode.VERIFICATION_RATE_LIMIT);
        }
        stringRedisTemplate.opsForValue().set(key, "1", interval);
    }

    /**
     * Caps the number of codes an identifier can receive per calendar day.
     * <p>
     * Uses a date-suffixed key so that midnight automatically rolls to a new counter
     * without needing a scheduled reset job. TTL is set only on the first increment
     * to prevent attackers from extending the key's lifetime through repeated requests.
     */
    private void enforceDailyLimit(VerificationScene scene, String identifier, int limit) {
        if (limit <= 0) return;
        String date = DAY_FORMAT.format(LocalDate.now());
        String key = "auth:code:count:" + scene.name() + ":" + identifier + ":" + date;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofDays(1));
        }
        if (count != null && count > limit) {
            throw new BusinessException(ErrorCode.VERIFICATION_DAILY_LIMIT);
        }
    }

    /** Generates a cryptographically random numeric code of the specified length. */
    private static String generateNumericCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }
}

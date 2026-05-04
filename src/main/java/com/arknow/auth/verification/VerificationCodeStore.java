package com.arknow.auth.verification;

import java.time.Duration;

/**
 * Contract for verification code persistence.
 * <p>
 * Implementations decide the storage backend. The current implementation uses Redis Hash
 * so that the three related fields (code, maxAttempts, attempts) share a single key, making
 * atomic operations and TTL management straightforward.
 */
public interface VerificationCodeStore {

    /**
     * Persist a new verification code with attempt tracking and expiry.
     *
     * @param scene       the business context (REGISTER, LOGIN, etc.)
     * @param identifier  the normalized phone or email
     * @param code        the generated numeric code
     * @param ttl         how long the code is valid
     * @param maxAttempts maximum wrong attempts before lockout
     */
    void saveCode(String scene, String identifier, String code, Duration ttl, int maxAttempts);

    /**
     * Validate a submitted code against the stored value.
     * <p>
     * On success, the code is deleted so it cannot be reused.
     * On failure, the attempt counter is incremented. When the counter reaches
     * maxAttempts, the TTL is extended to 30 minutes as a cooldown penalty.
     *
     * @return the result with status and attempt statistics
     */
    VerificationCheckResult verify(String scene, String identifier, String code);

    /** Remove the code record, e.g. after a successful operation. */
    void invalidate(String scene, String identifier);
}

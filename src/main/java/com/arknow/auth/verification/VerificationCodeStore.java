package com.arknow.auth.verification;

import java.time.Duration;

public interface VerificationCodeStore {
    void saveCode(String scene, String identifier, String code, Duration ttl, int maxAttempts);
    VerificationCheckResult verify(String scene, String identifier, String code);
    void invalidate(String scene, String identifier);
}

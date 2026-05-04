package com.arknow.auth.verification;

/**
 * Contract for delivering verification codes to users.
 * <p>
 * Decoupled from the business logic via the Strategy pattern: {@link LoggingCodeSender} logs
 * the code during development; a real SMS or email implementation can be swapped in later
 * without touching any consumer code.
 */
public interface CodeSender {
    void sendCode(VerificationScene scene, String identifier, String code, int expireMinutes);
}

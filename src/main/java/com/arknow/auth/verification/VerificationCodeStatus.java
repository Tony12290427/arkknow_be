package com.arknow.auth.verification;

/**
 * Possible outcomes of a verification code check.
 */
public enum VerificationCodeStatus {
    /** Code matched and was consumed. */
    SUCCESS,
    /** No code exists for this identifier+scene (never sent or expired). */
    NOT_FOUND,
    /** Code's TTL has elapsed. */
    EXPIRED,
    /** Code exists but the provided value does not match. */
    MISMATCH,
    /** All allowed attempts have been exhausted; further attempts are blocked. */
    TOO_MANY_ATTEMPTS
}

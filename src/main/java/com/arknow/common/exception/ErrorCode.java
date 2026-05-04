package com.arknow.common.exception;

/**
 * Unified error codes for business exceptions.
 * <p>
 * Each enum constant maps to a specific failure scenario. The {@link GlobalExceptionHandler}
 * translates these codes into HTTP status codes and JSON error responses.
 * <p>
 * Naming convention: domain-driven, uppercase with underscores.
 */
public enum ErrorCode {
    BAD_REQUEST,
    INTERNAL_ERROR,
    /** Account identifier already registered. */
    IDENTIFIER_EXISTS,
    /** No account found for the given identifier. */
    IDENTIFIER_NOT_FOUND,
    /** Wrong password or verification code. */
    INVALID_CREDENTIALS,
    /** Refresh token is expired, revoked, or malformed. */
    REFRESH_TOKEN_INVALID,
    /** User did not agree to service terms during registration. */
    TERMS_NOT_ACCEPTED,
    /** Verification code expired or was never sent. */
    VERIFICATION_NOT_FOUND,
    /** Verification code does not match. */
    VERIFICATION_MISMATCH,
    /** Too many failed attempts; further attempts are locked for a cooldown period. */
    VERIFICATION_TOO_MANY_ATTEMPTS,
    /** Same identifier tried to send another code before the send interval elapsed. */
    VERIFICATION_RATE_LIMIT,
    /** Daily verification code quota exceeded for this identifier. */
    VERIFICATION_DAILY_LIMIT,
    /** Password does not meet the configured policy. */
    PASSWORD_POLICY_VIOLATION
}

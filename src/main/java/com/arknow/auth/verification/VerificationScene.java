package com.arknow.auth.verification;

/**
 * The context in which a verification code is requested.
 * <p>
 * Different scenes enforce different business rules. For example, {@link #REGISTER} requires
 * the identifier not to exist, while {@link #LOGIN} requires it to exist.
 */
public enum VerificationScene {
    REGISTER, LOGIN, RESET_PASSWORD
}

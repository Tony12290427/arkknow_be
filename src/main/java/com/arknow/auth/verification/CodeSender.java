package com.arknow.auth.verification;

import com.arknow.auth.model.IdentifierType;

/**
 * Contract for delivering verification codes to users.
 * <p>
 * Implementations declare which {@link IdentifierType} they support via {@link #supports(IdentifierType)}.
 * The {@link VerificationService} injects all implementations as a {@code List<CodeSender>}
 * and routes to the matching one at runtime.
 */
public interface CodeSender {
    void sendCode(VerificationScene scene, String identifier, String code, int expireMinutes);

    boolean supports(IdentifierType type);
}

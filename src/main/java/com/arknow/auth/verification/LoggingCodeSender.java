package com.arknow.auth.verification;

import com.arknow.auth.model.IdentifierType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Development-only fallback {@link CodeSender} that writes the code to the application log.
 * <p>
 * Supports both {@link IdentifierType#PHONE} and {@link IdentifierType#EMAIL} so that
 * when neither a real SMS nor email sender is configured, this dev fallback handles
 * both types and prevents startup failures. Sorted last via {@code @Order} or
 * auto-detected by the routing logic.
 */
@Component
public class LoggingCodeSender implements CodeSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingCodeSender.class);

    @Override
    public boolean supports(IdentifierType type) {
        return true; // dev fallback for all types
    }

    @Override
    public void sendCode(VerificationScene scene, String identifier, String code, int expireMinutes) {
        log.info("Send verification code scene={} identifier={} code={} expireMinutes={}", scene, identifier, code, expireMinutes);
    }
}

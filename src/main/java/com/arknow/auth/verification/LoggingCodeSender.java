package com.arknow.auth.verification;

import com.arknow.auth.model.IdentifierType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Development-only fallback {@link CodeSender} that writes the code to the application log.
 * <p>
 * Ordered lowest so real senders (EmailCodeSender, SmsCodeSender) take priority
 * when their conditions are met.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
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

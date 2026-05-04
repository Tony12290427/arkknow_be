package com.arknow.auth.verification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingCodeSender implements CodeSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingCodeSender.class);

    @Override
    public void sendCode(VerificationScene scene, String identifier, String code, int expireMinutes) {
        log.info("Send verification code scene={} identifier={} code={} expireMinutes={}", scene, identifier, code, expireMinutes);
    }
}

package com.arknow.auth.verification;

import com.arknow.auth.model.IdentifierType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sms.access-key-id")
public class SmsCodeSender implements CodeSender {
    private static final Logger log = LoggerFactory.getLogger(SmsCodeSender.class);

    public SmsCodeSender() {
    }

    @Override
    public boolean supports(IdentifierType type) {
        return type == IdentifierType.PHONE;
    }

    @Override
    public void sendCode(VerificationScene scene, String identifier, String code, int expireMinutes) {
        log.info("SMS code would be sent to {}: code={} (SMS gateway pending template approval)", identifier, code);
        // TODO: Replace with real Alibaba Cloud SMS SDK call when template is approved
    }
}

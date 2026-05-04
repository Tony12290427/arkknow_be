package com.arknow.auth.verification;

public interface CodeSender {
    void sendCode(VerificationScene scene, String identifier, String code, int expireMinutes);
}

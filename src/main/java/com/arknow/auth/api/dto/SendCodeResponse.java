package com.arknow.auth.api.dto;

import com.arknow.auth.verification.VerificationScene;

public record SendCodeResponse(
        String identifier,
        VerificationScene scene,
        int expireSeconds
) {
}

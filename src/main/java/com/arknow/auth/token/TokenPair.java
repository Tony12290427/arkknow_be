package com.arknow.auth.token;

import java.time.Instant;

public record TokenPair(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        String refreshTokenId
) {
}

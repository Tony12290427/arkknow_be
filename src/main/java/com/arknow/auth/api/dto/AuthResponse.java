package com.arknow.auth.api.dto;

public record AuthResponse(
        AuthUserResponse user,
        TokenResponse token
) {
}

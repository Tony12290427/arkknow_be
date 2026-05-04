package com.arknow.auth.api.dto;

public record LogoutRequest(
        String refreshToken
) {
}

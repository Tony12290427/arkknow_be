package com.arknow.auth.api.dto;

public record ChannelLoginRequest(
        String type,
        String value,
        String username,
        String password
) {}

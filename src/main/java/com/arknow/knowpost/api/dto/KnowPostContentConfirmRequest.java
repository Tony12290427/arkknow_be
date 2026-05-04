package com.arknow.knowpost.api.dto;

public record KnowPostContentConfirmRequest(
        String objectKey, String etag, Long size, String sha256
) {}

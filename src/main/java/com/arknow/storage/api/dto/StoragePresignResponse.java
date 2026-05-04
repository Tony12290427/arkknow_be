package com.arknow.storage.api.dto;

import java.util.Map;

public record StoragePresignResponse(
        String objectKey, String putUrl, Map<String, String> headers, int expiresIn
) {}

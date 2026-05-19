package com.arknow.storage.api.dto;

public record StoragePresignRequest(
        String scene, String postId, String contentType, String ext, long size
) {}

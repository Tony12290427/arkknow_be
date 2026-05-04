package com.arknow.storage;

import com.arknow.storage.api.dto.StoragePresignRequest;
import com.arknow.storage.api.dto.StoragePresignResponse;
import com.arknow.storage.config.OssProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@EnableConfigurationProperties(OssProperties.class)
public class OssStorageService {
    private final OssProperties ossProperties;

    public OssStorageService(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
    }

    public StoragePresignResponse generatePresignedUrl(StoragePresignRequest request) {
        String objectKey = request.scene() + "/" + request.postId() + "/" + UUID.randomUUID() + request.ext();
        // In dev mode without real OSS, generate a placeholder presigned URL
        String putUrl = "http://localhost:8080/uploads/" + objectKey;
        return new StoragePresignResponse(objectKey, putUrl, Map.of("Content-Type", request.contentType()), 600);
    }
}

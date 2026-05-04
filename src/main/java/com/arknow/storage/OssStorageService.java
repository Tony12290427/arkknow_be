package com.arknow.storage;

import com.arknow.storage.api.dto.StoragePresignRequest;
import com.arknow.storage.api.dto.StoragePresignResponse;
import com.arknow.storage.config.OssProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Object storage service for presigned URL generation.
 * <p>
 * In production this would generate a time-limited presigned PUT URL from Alibaba Cloud OSS
 * so that clients can upload large files directly to object storage without routing bytes
 * through the application server. This avoids server bandwidth costs and bottlenecks.
 * <p>
 * The current dev implementation returns a placeholder URL. Replace with real OSS SDK calls
 * when deploying to production.
 */
@Service
@EnableConfigurationProperties(OssProperties.class)
public class OssStorageService {
    private final OssProperties ossProperties;

    public OssStorageService(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
    }

    /**
     * Generates a presigned upload URL.
     * <p>
     * The object key follows the pattern {@code <scene>/<postId>/<uuid><ext>} for
     * tenant isolation and collision avoidance.
     *
     * @param request scene, post ID, content type, and file extension
     * @return presigned URL details including object key, put URL, headers, and expiry
     */
    public StoragePresignResponse generatePresignedUrl(StoragePresignRequest request) {
        String objectKey = request.scene() + "/" + request.postId() + "/" + UUID.randomUUID() + request.ext();
        String putUrl = "http://localhost:8080/uploads/" + objectKey;
        return new StoragePresignResponse(objectKey, putUrl, Map.of("Content-Type", request.contentType()), 600);
    }
}

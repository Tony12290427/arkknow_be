package com.arknow.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.arknow.common.exception.BusinessException;
import com.arknow.common.exception.ErrorCode;
import com.arknow.storage.api.dto.StoragePresignRequest;
import com.arknow.storage.api.dto.StoragePresignResponse;
import com.arknow.storage.config.OssProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
@EnableConfigurationProperties(OssProperties.class)
public class OssStorageService {
    private final OSS ossClient;
    private final OssProperties ossProperties;

    public OssStorageService(OSS ossClient, OssProperties ossProperties) {
        this.ossClient = ossClient;
        this.ossProperties = ossProperties;
    }

    public StoragePresignResponse generatePresignedUrl(StoragePresignRequest request) {
        String scene = request.scene() != null ? request.scene() : "posts";
        String pid = request.postId() != null ? request.postId() : "draft";
        String ext = request.ext() != null ? request.ext() : ".png";
        String objectKey = scene + "/" + pid + "/" + UUID.randomUUID() + ext;

        try {
            Date expiration = new Date(System.currentTimeMillis() + 600 * 1000);
            GeneratePresignedUrlRequest presignReq = new GeneratePresignedUrlRequest(
                    ossProperties.getBucketName(), objectKey, HttpMethod.PUT);
            presignReq.setExpiration(expiration);
            presignReq.setContentType(request.contentType());

            URL url = ossClient.generatePresignedUrl(presignReq);
            String publicUrl = ossProperties.getCdnDomain() + "/" + objectKey;
            return new StoragePresignResponse(objectKey, url.toString(), publicUrl, Map.of("Content-Type", request.contentType()), 600);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to generate upload URL: " + e.getMessage());
        }
    }
}

package com.arknow.storage.api;

import com.arknow.common.exception.BusinessException;
import com.arknow.common.exception.ErrorCode;
import com.arknow.storage.OssStorageService;
import com.arknow.storage.api.dto.StoragePresignRequest;
import com.arknow.storage.api.dto.StoragePresignResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/storage")
public class StorageController {
    private final OssStorageService ossStorageService;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/webm", "video/mov"
    );

    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE = 100 * 1024 * 1024;

    public StorageController(OssStorageService ossStorageService) {
        this.ossStorageService = ossStorageService;
    }

    @PostMapping("/presign")
    public StoragePresignResponse presign(@RequestBody StoragePresignRequest request) {
        String contentType = request.contentType();
        if (contentType == null || contentType.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Content-Type is required");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported file type: " + contentType);
        }
        long maxSize = contentType.startsWith("video/") ? MAX_VIDEO_SIZE : MAX_IMAGE_SIZE;
        if (request.size() > maxSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "File too large. Maximum: " + (maxSize / 1024 / 1024) + " MB");
        }
        return ossStorageService.generatePresignedUrl(request);
    }
}

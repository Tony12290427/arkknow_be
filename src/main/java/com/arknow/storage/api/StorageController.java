package com.arknow.storage.api;

import com.arknow.storage.OssStorageService;
import com.arknow.storage.api.dto.StoragePresignRequest;
import com.arknow.storage.api.dto.StoragePresignResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/storage")
public class StorageController {
    private final OssStorageService ossStorageService;

    public StorageController(OssStorageService ossStorageService) {
        this.ossStorageService = ossStorageService;
    }

    @PostMapping("/presign")
    public StoragePresignResponse presign(@RequestBody StoragePresignRequest request) {
        return ossStorageService.generatePresignedUrl(request);
    }
}

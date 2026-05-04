package com.arknow.profile.api;

import com.arknow.profile.api.dto.ProfilePatchRequest;
import com.arknow.profile.api.dto.ProfileResponse;
import com.arknow.profile.service.ProfileService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
    private final ProfileService profileService;
    private static final Path UPLOAD_DIR = Paths.get(System.getProperty("user.dir"), "uploads", "avatars");

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getClaimAsString("uid"));
        return profileService.getProfile(userId);
    }

    @PatchMapping
    public ProfileResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
                                          @RequestBody ProfilePatchRequest request) {
        long userId = Long.parseLong(jwt.getClaimAsString("uid"));
        return profileService.updateProfile(userId, request);
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponse uploadAvatar(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam("file") MultipartFile file) {
        long userId = Long.parseLong(jwt.getClaimAsString("uid"));
        try {
            Files.createDirectories(UPLOAD_DIR);

            String ext = getExtension(file.getOriginalFilename());
            String filename = userId + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            Path filePath = UPLOAD_DIR.resolve(filename);
            file.transferTo(filePath);

            String avatarUrl = "/uploads/avatars/" + filename;
            return profileService.updateAvatar(userId, avatarUrl);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload avatar", e);
        }
    }

    private static String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }
}

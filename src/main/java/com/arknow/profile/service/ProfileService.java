package com.arknow.profile.service;

import com.arknow.profile.api.dto.ProfilePatchRequest;
import com.arknow.profile.api.dto.ProfileResponse;

import java.util.List;

public interface ProfileService {
    ProfileResponse getProfile(long userId);
    ProfileResponse updateProfile(long userId, ProfilePatchRequest request);
    ProfileResponse updateAvatar(long userId, String avatarUrl);
    List<String> getTags(long userId);
    void updateTags(long userId, List<String> tags);
}

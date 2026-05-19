package com.arknow.profile.service.impl;

import com.arknow.common.exception.BusinessException;
import com.arknow.common.exception.ErrorCode;
import com.arknow.profile.api.dto.ProfilePatchRequest;
import com.arknow.profile.api.dto.ProfileResponse;
import com.arknow.profile.service.ProfileService;
import com.arknow.user.domain.User;
import com.arknow.user.mapper.UserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * Profile management service.
 * <p>
 * Handles read, partial update, and avatar change for user profiles.
 * PATCH uses null-checking semantics: only non-null fields in the request are applied,
 * so clients can send just the fields they want to change.
 */
@Service
public class ProfileServiceImpl implements ProfileService {
    private final UserMapper userMapper;

    public ProfileServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public ProfileResponse getProfile(long userId) {
        User user = userMapper.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        return toResponse(user);
    }

    /**
     * Applies only the non-null fields from the request.
     * <p>
     * A field being {@code null} means "don't change it" — this is standard PATCH semantics.
     * Explicitly setting a field to blank/empty is how you clear it.
     */
    @Override
    public ProfileResponse updateProfile(long userId, ProfilePatchRequest request) {
        User user = userMapper.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));

        if (StringUtils.hasText(request.nickname())) user.setNickname(request.nickname());
        if (request.bio() != null) user.setBio(request.bio());
        if (request.gender() != null) user.setGender(request.gender());
        if (request.birthday() != null) user.setBirthday(request.birthday());
        if (request.zgId() != null) user.setZgId(request.zgId());
        if (request.school() != null) user.setSchool(request.school());
        if (request.tagJson() != null) user.setTagsJson(request.tagJson());

        userMapper.updateProfile(user);
        return toResponse(user);
    }

    @Override
    public ProfileResponse updateAvatar(long userId, String avatarUrl) {
        User user = userMapper.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        user.setAvatar(avatarUrl);
        userMapper.updateAvatar(user.getId(), avatarUrl);
        return toResponse(user);
    }

    @Override
    public List<String> getTags(long userId) {
        User user = userMapper.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        if (user.getTagsJson() == null || user.getTagsJson().isBlank()) {
            return Collections.emptyList();
        }
        try {
            return new ObjectMapper().readValue(user.getTagsJson(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public void updateTags(long userId, List<String> tags) {
        User user = userMapper.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        try {
            String tagsJson = new ObjectMapper().writeValueAsString(tags);
            userMapper.updateTags(userId, tagsJson);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private ProfileResponse toResponse(User user) {
        return new ProfileResponse(
                user.getId(), user.getNickname(), user.getAvatar(), user.getBio(),
                user.getZgId(), user.getGender(), user.getBirthday(), user.getSchool(),
                user.getPhone(), user.getEmail(), user.getTagsJson());
    }
}

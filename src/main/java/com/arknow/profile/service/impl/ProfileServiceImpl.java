package com.arknow.profile.service.impl;

import com.arknow.common.exception.BusinessException;
import com.arknow.common.exception.ErrorCode;
import com.arknow.profile.api.dto.ProfilePatchRequest;
import com.arknow.profile.api.dto.ProfileResponse;
import com.arknow.profile.service.ProfileService;
import com.arknow.user.domain.User;
import com.arknow.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    private ProfileResponse toResponse(User user) {
        return new ProfileResponse(
                user.getId(), user.getNickname(), user.getAvatar(), user.getBio(),
                user.getZgId(), user.getGender(), user.getBirthday(), user.getSchool(),
                user.getPhone(), user.getEmail(), user.getTagsJson());
    }
}

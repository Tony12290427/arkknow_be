package com.arknow.knowpost.api.dto;

import java.util.List;

public record KnowPostDetailResponse(
        String id, String title, String description, String contentUrl,
        List<String> images, List<String> tags, String authorAvatar,
        String authorNickname, String authorTagJson, Long authorId,
        Long likeCount, Long favoriteCount, Boolean liked, Boolean faved,
        Boolean isTop, String visible, String type, String publishTime
) {}

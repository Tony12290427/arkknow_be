package com.arknow.search.api.dto;

import java.util.List;

/**
 * Search result item with relevance score and business weight.
 */
public record SearchResponse(
        String id,
        String title,
        String description,
        String coverImage,
        List<String> tags,
        String authorNickname,
        String authorAvatar,
        long likeCount,
        double score
) {}

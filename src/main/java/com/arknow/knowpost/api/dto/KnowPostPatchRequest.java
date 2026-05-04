package com.arknow.knowpost.api.dto;

public record KnowPostPatchRequest(
        String title, Long tagId, String tags, String imgUrls,
        String visible, Boolean isTop, String description
) {}

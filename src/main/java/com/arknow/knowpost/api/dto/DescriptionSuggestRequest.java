package com.arknow.knowpost.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for AI description generation.
 */
public record DescriptionSuggestRequest(
        @NotBlank(message = "正文不能为空") String content
) {}

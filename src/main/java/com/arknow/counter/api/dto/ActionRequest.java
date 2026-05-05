package com.arknow.counter.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for like/unlike/fav/unfav actions.
 */
public record ActionRequest(
        @NotBlank String entityType,
        @NotBlank String entityId
) {}

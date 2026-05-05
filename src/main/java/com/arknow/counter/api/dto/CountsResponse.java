package com.arknow.counter.api.dto;

import java.util.Map;

/**
 * Response body for counter queries.
 */
public record CountsResponse(
        String entityType,
        String entityId,
        Map<String, Long> counts
) {}

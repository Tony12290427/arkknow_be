package com.arknow.search.api.dto;

import java.util.List;

/**
 * Prefix-based suggestion result.
 */
public record SuggestResponse(
        List<String> suggestions
) {}

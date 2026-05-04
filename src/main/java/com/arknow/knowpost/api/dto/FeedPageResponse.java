package com.arknow.knowpost.api.dto;

import java.util.List;

public record FeedPageResponse(
        List<FeedItemResponse> items, int page, int size, boolean hasMore
) {}

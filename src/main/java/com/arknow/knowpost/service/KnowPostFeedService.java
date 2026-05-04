package com.arknow.knowpost.service;

import com.arknow.knowpost.api.dto.FeedPageResponse;

public interface KnowPostFeedService {
    FeedPageResponse getPublicFeed(int page, int size, Long currentUserId);
}

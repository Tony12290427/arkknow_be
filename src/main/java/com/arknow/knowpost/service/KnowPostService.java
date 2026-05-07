package com.arknow.knowpost.service;

import com.arknow.knowpost.api.dto.*;

public interface KnowPostService {
    KnowPostDraftCreateResponse createDraft(long creatorId);
    void confirmContent(long postId, KnowPostContentConfirmRequest request);
    void updateMeta(long postId, KnowPostPatchRequest request);
    void publish(long postId);
    void setTop(long postId, boolean isTop);
    void setVisibility(long postId, String visible);
    void softDelete(long postId, long creatorId);
    KnowPostDetailResponse getDetail(long postId, Long currentUserId);
    FeedPageResponse getMyPosts(long creatorId, int page, int size);
    FeedPageResponse getFollowingFeed(long userId, int page, int size);
    FeedPageResponse getLikedPosts(long userId, int page, int size);
    FeedPageResponse getFavedPosts(long userId, int page, int size);
}

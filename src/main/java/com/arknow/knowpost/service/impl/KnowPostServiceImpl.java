package com.arknow.knowpost.service.impl;

import com.arknow.common.exception.BusinessException;
import com.arknow.common.exception.ErrorCode;
import com.arknow.counter.service.CounterService;
import com.arknow.counter.service.UserCounterService;
import com.arknow.knowpost.api.dto.*;
import com.arknow.knowpost.id.SnowflakeIdGenerator;
import com.arknow.knowpost.mapper.KnowPostMapper;
import com.arknow.knowpost.model.KnowPost;
import com.arknow.knowpost.model.KnowPostDetailRow;
import com.arknow.knowpost.model.KnowPostFeedRow;
import com.arknow.knowpost.service.KnowPostService;
import com.arknow.search.index.SearchIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Knowledge post CRUD and lifecycle management.
 * <p>
 * Implements a progressive publishing workflow:
 * <ol>
 *   <li>Create draft (returns snowflake ID immediately)</li>
 *   <li>Get OSS presigned URL and upload content client-side</li>
 *   <li>Confirm content upload with ETag/SHA256 checksums</li>
 *   <li>Update metadata (title, tags, images)</li>
 *   <li>Publish (draft → published, sets publish_time)</li>
 * </ol>
 * Each step is idempotent — repeating any step has no side effects, allowing clients
 * to safely retry on network failures.
 */
@Service
public class KnowPostServiceImpl implements KnowPostService {
    private static final Logger log = LoggerFactory.getLogger(KnowPostServiceImpl.class);

    private final KnowPostMapper mapper;
    private final SnowflakeIdGenerator idGen;
    private final CounterService counterService;
    private final SearchIndexService searchIndexService;
    private final StringRedisTemplate redis;
    private final UserCounterService userCounterService;

    public KnowPostServiceImpl(KnowPostMapper mapper, SnowflakeIdGenerator idGen,
                                CounterService counterService,
                                @Autowired(required = false) SearchIndexService searchIndexService,
                                StringRedisTemplate redis,
                                UserCounterService userCounterService) {
        this.mapper = mapper;
        this.idGen = idGen;
        this.counterService = counterService;
        this.searchIndexService = searchIndexService;
        this.redis = redis;
        this.userCounterService = userCounterService;
    }

    @Override
    public KnowPostDraftCreateResponse createDraft(long creatorId) {
        long id = idGen.nextId();
        KnowPost post = new KnowPost();
        post.setId(id);
        post.setCreatorId(creatorId);
        post.setStatus("draft");
        post.setType("image_text");
        post.setVisible("public");
        post.setIsTop(false);
        mapper.insert(post);
        return new KnowPostDraftCreateResponse(String.valueOf(id));
    }

    @Override
    public void confirmContent(long postId, KnowPostContentConfirmRequest request) {
        String contentUrl = "http://localhost:8080/uploads/" + request.objectKey();
        mapper.updateContentConfirm(postId, request.objectKey(), request.etag(), request.size(), request.sha256(), contentUrl);
    }

    @Override
    public void updateMeta(long postId, KnowPostPatchRequest request) {
        KnowPost post = mapper.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        if (request.title() != null) post.setTitle(request.title());
        if (request.tagId() != null) post.setTagId(request.tagId());
        if (request.tags() != null) post.setTags(request.tags());
        if (request.imgUrls() != null) post.setImgUrls(request.imgUrls());
        if (request.description() != null) post.setDescription(request.description());
        if (request.visible() != null) post.setVisible(request.visible());
        if (request.isTop() != null) post.setIsTop(request.isTop());
        mapper.update(post);
    }

    /**
     * Transitions a draft to published.
     * <p>
     * A dedicated SQL statement is used (not the generic update) to atomically set
     * both {@code status='published'} and {@code publish_time=NOW()}.
     */
    @Override
    @Transactional
    public void publish(long postId) {
        KnowPost post = mapper.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        if (!"draft".equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只能发布草稿状态的内容");
        }
        mapper.publish(postId, java.time.Instant.now());
        // Increment user post counter (log error but don't block publish)
        try {
            userCounterService.incrementPosts(post.getCreatorId(), 1);
        } catch (Exception e) {
            log.warn("Failed to increment post counter for user {}: {}", post.getCreatorId(), e.getMessage());
        }
        // Sync to ES search index (no-op if ES not configured)
        if (searchIndexService != null) {
            try { searchIndexService.upsertKnowPost(postId); } catch (Exception ignored) {}
        }
    }

    @Override
    public void setTop(long postId, boolean isTop) {
        mapper.updateTop(postId, isTop);
    }

    @Override
    public void setVisibility(long postId, String visible) {
        mapper.updateVisibility(postId, visible);
    }

    /** Soft-deletes a post by setting status to 'deleted'. The creator must be the owner. */
    @Override
    public void softDelete(long postId, long creatorId) {
        int rows = mapper.softDelete(postId, creatorId);
        if (rows == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "删除失败");
    }

    /**
     * Returns full post detail with current-user-specific like/fav state.
     * <p>
     * User state (liked, faved) is computed at read time and NOT cached — it belongs
     * to the individual user, not the public resource.
     */
    @Override
    public KnowPostDetailResponse getDetail(long postId, Long currentUserId) {
        KnowPostDetailRow row = mapper.findDetailById(postId);
        if (row == null) throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND);

        boolean liked = currentUserId != null && counterService.isLiked("knowpost", String.valueOf(postId), currentUserId);
        boolean faved = currentUserId != null && counterService.isFaved("knowpost", String.valueOf(postId), currentUserId);
        Map<String, Long> counts = counterService.getCounts("knowpost", String.valueOf(postId), List.of("like", "fav"));

        return new KnowPostDetailResponse(
                row.getId(), row.getTitle(), row.getDescription(), row.getContentUrl(),
                parseArray(row.getImgUrls()), parseArray(row.getTags()),
                row.getAuthorAvatar(), row.getAuthorNickname(), row.getAuthorTagJson(),
                row.getCreatorId(),
                counts.getOrDefault("like", 0L), counts.getOrDefault("fav", 0L),
                liked, faved, row.getIsTop(), row.getVisible(), row.getType(), row.getPublishTime());
    }

    @Override
    public FeedPageResponse getFollowingFeed(long userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 1);
        int offset = (safePage - 1) * safeSize;
        List<KnowPostFeedRow> rows = mapper.listFeedByFollowing(userId, safeSize + 1, offset);
        boolean hasMore = rows.size() > safeSize;
        if (hasMore) rows = rows.subList(0, safeSize);
        List<FeedItemResponse> items = rows.stream().map(r -> toFeedItem(r, userId)).toList();
        return new FeedPageResponse(items, safePage, safeSize, hasMore);
    }

    @Override
    public FeedPageResponse getMyPosts(long creatorId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 1);
        int offset = (safePage - 1) * safeSize;

        List<KnowPostFeedRow> rows = mapper.listFeedByCreator(creatorId, safeSize + 1, offset);
        boolean hasMore = rows.size() > safeSize;
        if (hasMore) rows = rows.subList(0, safeSize);

        List<FeedItemResponse> items = rows.stream().map(r -> toFeedItem(r, creatorId)).toList();
        return new FeedPageResponse(items, safePage, safeSize, hasMore);
    }

    @Override
    public FeedPageResponse getLikedPosts(long userId, int page, int size) {
        return getPostsByRedisSet("user:likes:" + userId, userId, page, size);
    }

    @Override
    public FeedPageResponse getFavedPosts(long userId, int page, int size) {
        return getPostsByRedisSet("user:favs:" + userId, userId, page, size);
    }

    private FeedPageResponse getPostsByRedisSet(String redisKey, long userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 1);
        int offset = (safePage - 1) * safeSize;

        // Get all post IDs from Redis Set (sorted by insertion order = most recent first)
        Set<String> rawIds = redis.opsForSet().members(redisKey);
        if (rawIds == null || rawIds.isEmpty()) {
            return new FeedPageResponse(List.of(), safePage, safeSize, false);
        }

        // Sort by ID descending (approximate recency) and paginate
        List<Long> allIds = rawIds.stream()
                .map(Long::parseLong)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        int end = Math.min(offset + safeSize + 1, allIds.size());
        List<Long> pageIds = allIds.subList(offset, Math.min(end, allIds.size()));
        boolean hasMore = end < allIds.size();

        // Limit to safeSize+1 for hasMore detection
        if (pageIds.size() > safeSize) {
            pageIds = pageIds.subList(0, safeSize);
            hasMore = true;
        }

        if (pageIds.isEmpty()) {
            return new FeedPageResponse(List.of(), safePage, safeSize, hasMore);
        }

        List<KnowPostFeedRow> rows = mapper.listFeedByIds(pageIds);
        List<FeedItemResponse> items = rows.stream().map(r -> toFeedItem(r, userId)).toList();
        return new FeedPageResponse(items, safePage, safeSize, hasMore);
    }

    private FeedItemResponse toFeedItem(KnowPostFeedRow r, long currentUserId) {
        List<String> imgs = parseArray(r.getImgUrls());
        boolean liked = counterService.isLiked("knowpost", r.getId(), currentUserId);
        boolean faved = counterService.isFaved("knowpost", r.getId(), currentUserId);
        Map<String, Long> counts = counterService.getCounts("knowpost", r.getId(), List.of("like", "fav"));

        return new FeedItemResponse(
                r.getId(), r.getTitle(), r.getDescription(), imgs.isEmpty() ? null : imgs.getFirst(),
                parseArray(r.getTags()), r.getAuthorAvatar(), r.getAuthorNickname(),
                r.getAuthorTagJson(), counts.getOrDefault("like", 0L), counts.getOrDefault("fav", 0L),
                liked, faved, r.getIsTop());
    }

    /** Parses a JSON array string into a Java List. Returns empty list for null/empty input. */
    private static List<String> parseArray(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) return List.of();
        if (json.startsWith("[")) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, List.class);
            } catch (Exception e) {
                return List.of();
            }
        }
        return List.of(json);
    }
}

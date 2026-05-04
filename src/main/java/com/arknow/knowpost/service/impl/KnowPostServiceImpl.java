package com.arknow.knowpost.service.impl;

import com.arknow.common.exception.BusinessException;
import com.arknow.common.exception.ErrorCode;
import com.arknow.counter.service.CounterService;
import com.arknow.knowpost.api.dto.*;
import com.arknow.knowpost.id.SnowflakeIdGenerator;
import com.arknow.knowpost.mapper.KnowPostMapper;
import com.arknow.knowpost.model.KnowPost;
import com.arknow.knowpost.model.KnowPostDetailRow;
import com.arknow.knowpost.model.KnowPostFeedRow;
import com.arknow.knowpost.service.KnowPostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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
    private final KnowPostMapper mapper;
    private final SnowflakeIdGenerator idGen;
    private final CounterService counterService;

    public KnowPostServiceImpl(KnowPostMapper mapper, SnowflakeIdGenerator idGen,
                                CounterService counterService) {
        this.mapper = mapper;
        this.idGen = idGen;
        this.counterService = counterService;
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
        mapper.updateContentConfirm(postId, request.objectKey(), request.etag(), request.size(), request.sha256());
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
                counts.getOrDefault("like", 0L), counts.getOrDefault("fav", 0L),
                liked, faved, row.getIsTop(), row.getVisible(), row.getType(), row.getPublishTime());
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

package com.arknow.knowpost.api;

import com.arknow.knowpost.api.dto.*;
import com.arknow.knowpost.service.KnowPostFeedService;
import com.arknow.knowpost.service.KnowPostService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Knowledge post REST API controller.
 * <p>
 * Exposes the complete post lifecycle: draft creation, content confirmation,
 * metadata editing, publishing, visibility control, pinning, soft deletion,
 * public feed browsing, detail views, and personal post listing.
 * <p>
 * All mutating endpoints require authentication. Feed and detail are public
 * but accept an optional Bearer token to compute per-user state (liked/faved).
 */
@RestController
@RequestMapping("/api/v1/knowposts")
public class KnowPostController {
    private final KnowPostService knowPostService;
    private final KnowPostFeedService feedService;

    public KnowPostController(KnowPostService knowPostService, KnowPostFeedService feedService) {
        this.knowPostService = knowPostService;
        this.feedService = feedService;
    }

    @PostMapping("/drafts")
    public KnowPostDraftCreateResponse createDraft(@AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getClaimAsString("uid"));
        return knowPostService.createDraft(userId);
    }

    @PostMapping("/{id}/content/confirm")
    public ResponseEntity<Void> confirmContent(@PathVariable long id,
                                                @RequestBody KnowPostContentConfirmRequest request) {
        knowPostService.confirmContent(id, request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateMeta(@PathVariable long id,
                                            @RequestBody KnowPostPatchRequest request) {
        knowPostService.updateMeta(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<Void> publish(@PathVariable long id) {
        knowPostService.publish(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/top")
    public ResponseEntity<Void> setTop(@PathVariable long id,
                                        @RequestBody KnowPostTopPatchRequest request) {
        knowPostService.setTop(id, request.isTop());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/visibility")
    public ResponseEntity<Void> setVisibility(@PathVariable long id,
                                               @RequestBody KnowPostVisibilityPatchRequest request) {
        knowPostService.setVisibility(id, request.visible());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(@PathVariable long id,
                                            @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getClaimAsString("uid"));
        knowPostService.softDelete(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Public feed with optional authentication.
     * When a valid Bearer token is present, each item includes the current user's
     * liked/faved state. Without a token, these fields are false.
     */
    @GetMapping("/feed")
    public FeedPageResponse feed(@RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt != null ? Long.parseLong(jwt.getClaimAsString("uid")) : null;
        return feedService.getPublicFeed(page, size, userId);
    }

    @GetMapping("/detail/{id}")
    public KnowPostDetailResponse detail(@PathVariable long id,
                                          @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt != null ? Long.parseLong(jwt.getClaimAsString("uid")) : null;
        return knowPostService.getDetail(id, userId);
    }

    @GetMapping("/mine")
    public FeedPageResponse myPosts(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getClaimAsString("uid"));
        return knowPostService.getMyPosts(userId, page, size);
    }

    @GetMapping("/following")
    public FeedPageResponse followingFeed(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getClaimAsString("uid"));
        return knowPostService.getFollowingFeed(userId, page, size);
    }
}

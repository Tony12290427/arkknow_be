package com.arknow.relation.api;

import com.arknow.profile.api.dto.ProfileResponse;
import com.arknow.relation.service.RelationService;
import com.arknow.relation.service.RelationService.RelationStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * User relationship REST API controller.
 * <p>
 * Exposes follow, unfollow, relationship status queries, and following/follower lists.
 * All endpoints require authentication.
 */
@RestController
@RequestMapping("/api/v1/relation")
public class RelationController {
    private final RelationService relationService;

    public RelationController(RelationService relationService) {
        this.relationService = relationService;
    }

    /**
     * Follow a user. Rate-limited via token bucket.
     *
     * @param toUserId the user to follow
     * @return true if a new relationship was created
     */
    @PostMapping("/follow")
    public Map<String, Boolean> follow(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam long toUserId) {
        long fromUserId = Long.parseLong(jwt.getClaimAsString("uid"));
        boolean result = relationService.follow(fromUserId, toUserId);
        return Map.of("success", result);
    }

    /**
     * Unfollow a user.
     *
     * @param toUserId the user to unfollow
     * @return true if the relationship was removed
     */
    @PostMapping("/unfollow")
    public Map<String, Boolean> unfollow(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam long toUserId) {
        long fromUserId = Long.parseLong(jwt.getClaimAsString("uid"));
        boolean result = relationService.unfollow(fromUserId, toUserId);
        return Map.of("success", result);
    }

    /**
     * Returns the three-state relationship between the current user and the target user.
     * States: following, followedBy, mutual (both follow each other).
     */
    @GetMapping("/status")
    public RelationStatus status(@AuthenticationPrincipal Jwt jwt,
                                  @RequestParam long toUserId) {
        long fromUserId = Long.parseLong(jwt.getClaimAsString("uid"));
        return relationService.getStatus(fromUserId, toUserId);
    }

    /** Returns users the specified user is following. */
    @GetMapping("/following")
    public List<ProfileResponse> following(@RequestParam long userId,
                                            @RequestParam(defaultValue = "20") int limit,
                                            @RequestParam(defaultValue = "0") int offset) {
        return relationService.listFollowing(userId, limit, offset);
    }

    /** Returns the followers of the specified user. */
    @GetMapping("/followers")
    public List<ProfileResponse> followers(@RequestParam long userId,
                                            @RequestParam(defaultValue = "20") int limit,
                                            @RequestParam(defaultValue = "0") int offset) {
        return relationService.listFollowers(userId, limit, offset);
    }
}

package com.arknow.counter.api;

import com.arknow.counter.api.dto.ActionRequest;
import com.arknow.counter.service.impl.CounterServiceImpl;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * User action REST controller — like, unlike, favorite, unfavorite.
 * <p>
 * All endpoints require authentication. Each operation is idempotent:
 * double-liking is a no-op (bitmap already has 1 at that position).
 */
@RestController
@RequestMapping("/api/v1/action")
public class ActionController {
    private final CounterServiceImpl counterService;

    public ActionController(CounterServiceImpl counterService) {
        this.counterService = counterService;
    }

    @PostMapping("/like")
    public Map<String, Object> like(@RequestBody ActionRequest req, @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getClaimAsString("uid"));
        boolean changed = counterService.like(req.entityType(), req.entityId(), uid);
        return Map.of("changed", changed, "liked", counterService.isLiked(req.entityType(), req.entityId(), uid));
    }

    @PostMapping("/unlike")
    public Map<String, Object> unlike(@RequestBody ActionRequest req, @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getClaimAsString("uid"));
        boolean changed = counterService.unlike(req.entityType(), req.entityId(), uid);
        return Map.of("changed", changed, "liked", counterService.isLiked(req.entityType(), req.entityId(), uid));
    }

    @PostMapping("/fav")
    public Map<String, Object> fav(@RequestBody ActionRequest req, @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getClaimAsString("uid"));
        boolean changed = counterService.fav(req.entityType(), req.entityId(), uid);
        return Map.of("changed", changed, "faved", counterService.isFaved(req.entityType(), req.entityId(), uid));
    }

    @PostMapping("/unfav")
    public Map<String, Object> unfav(@RequestBody ActionRequest req, @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getClaimAsString("uid"));
        boolean changed = counterService.unfav(req.entityType(), req.entityId(), uid);
        return Map.of("changed", changed, "faved", counterService.isFaved(req.entityType(), req.entityId(), uid));
    }
}

package com.arknow.counter.api;

import com.arknow.counter.api.dto.ActionRequest;
import com.arknow.counter.service.UserCounterService;
import com.arknow.counter.service.impl.CounterServiceImpl;
import com.arknow.knowpost.id.SnowflakeIdGenerator;
import com.arknow.knowpost.mapper.KnowPostMapper;
import com.arknow.knowpost.model.KnowPost;
import com.arknow.notification.mapper.NotificationMapper;
import com.arknow.notification.model.Notification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

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
    private final NotificationMapper notifMapper;
    private final KnowPostMapper postMapper;
    private final SnowflakeIdGenerator idGen;
    private final StringRedisTemplate redis;
    private final UserCounterService userCounterService;

    private static final String USER_LIKES_KEY = "user:likes:";
    private static final String USER_FAVS_KEY = "user:favs:";

    public ActionController(CounterServiceImpl counterService, NotificationMapper notifMapper,
                            KnowPostMapper postMapper, SnowflakeIdGenerator idGen,
                            StringRedisTemplate redis, UserCounterService userCounterService) {
        this.counterService = counterService;
        this.notifMapper = notifMapper;
        this.postMapper = postMapper;
        this.idGen = idGen;
        this.redis = redis;
        this.userCounterService = userCounterService;
    }

    @PostMapping("/like")
    public Map<String, Object> like(@RequestBody ActionRequest req, @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getClaimAsString("uid"));
        boolean changed = counterService.like(req.entityType(), req.entityId(), uid);
        if (changed) {
            redis.opsForSet().add(USER_LIKES_KEY + uid, req.entityId());
            notifyPostAuthor(uid, Long.parseLong(req.entityId()), "like");
        }
        return Map.of("changed", changed, "liked", counterService.isLiked(req.entityType(), req.entityId(), uid));
    }

    @PostMapping("/unlike")
    public Map<String, Object> unlike(@RequestBody ActionRequest req, @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getClaimAsString("uid"));
        boolean changed = counterService.unlike(req.entityType(), req.entityId(), uid);
        if (changed) redis.opsForSet().remove(USER_LIKES_KEY + uid, req.entityId());
        return Map.of("changed", changed, "liked", counterService.isLiked(req.entityType(), req.entityId(), uid));
    }

    @PostMapping("/fav")
    public Map<String, Object> fav(@RequestBody ActionRequest req, @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getClaimAsString("uid"));
        boolean changed = counterService.fav(req.entityType(), req.entityId(), uid);
        if (changed) {
            redis.opsForSet().add(USER_FAVS_KEY + uid, req.entityId());
            notifyPostAuthor(uid, Long.parseLong(req.entityId()), "fav");
        }
        return Map.of("changed", changed, "faved", counterService.isFaved(req.entityType(), req.entityId(), uid));
    }

    @PostMapping("/unfav")
    public Map<String, Object> unfav(@RequestBody ActionRequest req, @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getClaimAsString("uid"));
        boolean changed = counterService.unfav(req.entityType(), req.entityId(), uid);
        if (changed) redis.opsForSet().remove(USER_FAVS_KEY + uid, req.entityId());
        return Map.of("changed", changed, "faved", counterService.isFaved(req.entityType(), req.entityId(), uid));
    }

    private void notifyPostAuthor(long actorId, long postId, String type) {
        try {
            Optional<KnowPost> post = postMapper.findById(postId);
            if (post.isEmpty()) return;
            long authorId = post.get().getCreatorId();
            // Increment user counter
            if ("like".equals(type)) {
                userCounterService.incrementLikedPosts(authorId, 1);
            } else if ("fav".equals(type)) {
                userCounterService.incrementFavedPosts(authorId, 1);
            }
            if (authorId == actorId) return;
            Notification n = new Notification();
            n.setId(idGen.nextId());
            n.setUserId(authorId);
            n.setType(type);
            n.setActorId(actorId);
            n.setPostId(postId);
            notifMapper.insert(n);
        } catch (Exception ignored) {}
    }
}

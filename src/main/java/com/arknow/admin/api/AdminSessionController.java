package com.arknow.admin.api;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/admin/sessions")
public class AdminSessionController {

    private final StringRedisTemplate redis;

    public AdminSessionController(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @GetMapping
    public Map<String, Object> list() {
        Set<String> keys = redis.keys("refresh:*");
        List<Map<String, Object>> sessions = new ArrayList<>();
        if (keys != null) {
            for (String key : keys) {
                String userId = key.substring("refresh:".length());
                Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
                sessions.add(Map.of("key", key, "userId", userId, "ttlSeconds", ttl != null ? ttl : -1));
            }
        }
        return Map.of("items", sessions, "total", sessions.size());
    }

    @DeleteMapping("/{userId}")
    public Map<String, Object> revoke(@PathVariable String userId) {
        Set<String> keys = redis.keys("refresh:" + userId + "*");
        long deleted = 0;
        if (keys != null) {
            deleted = redis.delete(keys);
        }
        return Map.of("success", true, "deleted", deleted);
    }
}

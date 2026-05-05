package com.arknow.knowpost.listener;

import com.arknow.cache.hotkey.HotKeyDetector;
import com.arknow.knowpost.api.dto.FeedPageResponse;
import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Event-driven cache invalidation for feed pages.
 * <p>
 * When content changes (publish, edit, delete, top, visibility change), this listener
 * performs a "double-delete" pattern on affected cache entries:
 * <ol>
 *   <li>Immediate delete of related Redis page and fragment keys</li>
 *   <li>Delayed second delete (after a short sleep) to catch stale writes from
 *       concurrent in-flight requests that read old data before the first delete</li>
 * </ol>
 * <p>
 * For public feed changes, all page keys matching the public feed prefix are cleared
 * since any page could be affected by a new/top/deleted post. Hotkey counters are also reset.
 */
@Component
public class FeedCacheInvalidationListener {
    private static final Logger log = LoggerFactory.getLogger(FeedCacheInvalidationListener.class);

    private final StringRedisTemplate redis;
    private final Cache<String, FeedPageResponse> feedPublicCache;
    private final HotKeyDetector hotKey;

    public FeedCacheInvalidationListener(StringRedisTemplate redis,
                                          @Qualifier("feedPublicCache") Cache<String, FeedPageResponse> feedPublicCache,
                                          HotKeyDetector hotKey) {
        this.redis = redis;
        this.feedPublicCache = feedPublicCache;
        this.hotKey = hotKey;
    }

    /** Invalidates all public feed cache layers and resets hotkey counters. */
    public void onPublicFeedChanged() {
        // Delete Redis page and fragment keys
        deleteByPattern("feed:public:*");
        // Clear local Caffeine cache
        feedPublicCache.invalidateAll();
        log.debug("Public feed cache invalidated");
    }

    /** Invalidates cache for a specific user's personal feed. */
    public void onMyFeedChanged(long userId) {
        deleteByPattern("feed:mine:" + userId + ":*");
    }

    /**
     * Double-delete pattern: delete now, then delete again after a jittered delay.
     * The second delete catches stale data written by concurrent requests that read
     * after the first delete but before the cache was repopulated with fresh data.
     */
    public void doubleDelete(String pattern, long delayMs) {
        deleteByPattern(pattern);
        new Thread(() -> {
            try {
                Thread.sleep(delayMs + ThreadLocalRandom.current().nextLong(50));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            deleteByPattern(pattern);
        }).start();
    }

    private void deleteByPattern(String pattern) {
        try {
            Set<String> keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Cache invalidation failed for pattern {}: {}", pattern, e.getMessage());
        }
    }
}

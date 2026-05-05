package com.arknow.knowpost.service.impl;

import com.arknow.cache.config.CacheProperties;
import com.arknow.cache.hotkey.HotKeyDetector;
import com.arknow.counter.service.CounterService;
import com.arknow.knowpost.api.dto.FeedItemResponse;
import com.arknow.knowpost.api.dto.FeedPageResponse;
import com.arknow.knowpost.mapper.KnowPostMapper;
import com.arknow.knowpost.model.KnowPostFeedRow;
import com.arknow.knowpost.service.KnowPostFeedService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Public feed service with three-tier caching.
 * <p>
 * Cache hierarchy (from fastest/cheapest to slowest):
 * <ul>
 *   <li><b>L2 — Caffeine local cache</b>: complete {@link FeedPageResponse}, stored
 *       in application memory. Zero network cost. Hottest pages land here.</li>
 *   <li><b>L1 — Redis page skeleton</b>: ID list + hasMore flag. Cheaper than assembling
 *       full items. Hit here means only L0 reads are needed.</li>
 *   <li><b>L0 — Redis fragments</b>: per-item metadata (title, author, cover, counts).
 *       Assembled into page responses using the L1 ID list.</li>
 * </ul>
 * <p>
 * <b>Single-flight</b>: when a cache miss reaches the database, only one request per page
 * is allowed to query. Others block and re-check the cache. This prevents the thundering
 * herd problem when a hot page expires.
 * <p>
 * <b>Hotkey TTL extension</b>: pages detected as hot via sliding-window access counts
 * get progressively longer cache TTLs, keeping popular content in faster layers.
 */
@Service
public class KnowPostFeedServiceImpl implements KnowPostFeedService {
    private static final Logger log = LoggerFactory.getLogger(KnowPostFeedServiceImpl.class);

    private final KnowPostMapper mapper;
    private final CounterService counterService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Cache<String, FeedPageResponse> feedPublicCache;
    private final HotKeyDetector hotKey;
    private final CacheProperties cacheProperties;
    private final ConcurrentHashMap<String, Object> singleFlight = new ConcurrentHashMap<>();

    public KnowPostFeedServiceImpl(KnowPostMapper mapper, CounterService counterService,
                                    StringRedisTemplate redis, ObjectMapper objectMapper,
                                    @Qualifier("feedPublicCache") Cache<String, FeedPageResponse> feedPublicCache,
                                    HotKeyDetector hotKey, CacheProperties cacheProperties) {
        this.mapper = mapper;
        this.counterService = counterService;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.feedPublicCache = feedPublicCache;
        this.hotKey = hotKey;
        this.cacheProperties = cacheProperties;
    }

    @Override
    public FeedPageResponse getPublicFeed(int page, int size, Long currentUserId) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 1);
        String cacheKey = "feed:public:v1:" + safeSize + ":" + safePage;

        // ============ L2: Caffeine local cache ============
        FeedPageResponse local = feedPublicCache.getIfPresent(cacheKey);
        if (local != null) {
            hotKey.record(cacheKey);
            log.debug("feed source=local page={}", safePage);
            return enrich(local, currentUserId);
        }

        // ============ L1+L0: Redis page + fragments ============
        long hourSlot = System.currentTimeMillis() / 3600000L;
        String idsKey = "feed:public:ids:" + safeSize + ":" + hourSlot + ":" + safePage;
        String hasMoreKey = idsKey + ":hasMore";

        FeedPageResponse fromCache = assembleFromRedis(idsKey, hasMoreKey, safePage, safeSize, currentUserId);
        if (fromCache != null) {
            feedPublicCache.put(cacheKey, fromCache);
            hotKey.record(cacheKey);
            maybeExtendTtl(cacheKey);
            log.debug("feed source=redis page={}", safePage);
            return fromCache;
        }

        // ============ Database with single-flight ============
        Object lock = singleFlight.computeIfAbsent(idsKey, k -> new Object());
        synchronized (lock) {
            try {
                // Re-check caches after acquiring lock
                FeedPageResponse again = assembleFromRedis(idsKey, hasMoreKey, safePage, safeSize, currentUserId);
                if (again != null) {
                    feedPublicCache.put(cacheKey, again);
                    hotKey.record(cacheKey);
                    maybeExtendTtl(cacheKey);
                    return again;
                }

                // Database query: fetch size+1 to determine hasMore
                int offset = (safePage - 1) * safeSize;
                List<KnowPostFeedRow> rows = mapper.listFeedPublic(safeSize + 1, offset);
                boolean hasMore = rows.size() > safeSize;
                if (hasMore) rows = rows.subList(0, safeSize);

                List<FeedItemResponse> items = rows.stream()
                        .map(r -> toFeedItem(r, currentUserId)).toList();
                FeedPageResponse resp = new FeedPageResponse(items, safePage, safeSize, hasMore);

                // Write back to all cache layers
                int baseTtl = cacheProperties.getFragmentTtl();
                int jitter = ThreadLocalRandom.current().nextInt(30);
                Duration frTtl = Duration.ofSeconds(baseTtl + jitter);
                Duration pageTtl = Duration.ofSeconds(cacheProperties.getPageTtl() + ThreadLocalRandom.current().nextInt(11));
                writeToCache(idsKey, hasMoreKey, rows, items, hasMore, frTtl, pageTtl);

                feedPublicCache.put(cacheKey, resp);
                hotKey.record(cacheKey);
                log.debug("feed source=db page={} hasMore={}", safePage, hasMore);
                return resp;
            } finally {
                singleFlight.remove(idsKey);
            }
        }
    }

    /**
     * Assembles a page from Redis L1 (ID list) + L0 (fragments).
     * Returns null if the L1 skeleton is missing (cache miss).
     */
    private FeedPageResponse assembleFromRedis(String idsKey, String hasMoreKey,
                                                int page, int size, Long currentUserId) {
        String hasMoreStr = redis.opsForValue().get(hasMoreKey);
        if (hasMoreStr == null) return null;

        // Read ID list from L1
        String idsJson = redis.opsForValue().get(idsKey);
        if (idsJson == null) return null;

        List<String> idList;
        try {
            idList = objectMapper.readValue(idsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return null;
        }
        if (idList.isEmpty()) return null;

        // Batch read fragments from L0
        List<FeedItemResponse> items = new ArrayList<>(idList.size());
        for (String id : idList) {
            String itemKey = "feed:item:" + id;
            String ij = redis.opsForValue().get(itemKey);
            if (ij == null) return null; // Fragment missing, fall through to DB
            if ("NULL".equals(ij)) { items.add(null); continue; }
            try {
                items.add(objectMapper.readValue(ij, FeedItemResponse.class));
            } catch (Exception e) {
                return null;
            }
        }
        // Remove null placeholders and enrich
        items.removeIf(it -> it == null);
        boolean hasMore = "true".equals(hasMoreStr);
        return new FeedPageResponse(items, page, size, hasMore);
    }

    /** Writes page skeleton (L1) and item fragments (L0) to Redis. */
    private void writeToCache(String idsKey, String hasMoreKey, List<KnowPostFeedRow> rows,
                               List<FeedItemResponse> items, boolean hasMore,
                               Duration frTtl, Duration pageTtl) {
        try {
            // L1: page skeleton
            List<String> ids = rows.stream().map(KnowPostFeedRow::getId).toList();
            redis.opsForValue().set(idsKey, objectMapper.writeValueAsString(ids), pageTtl);
            redis.opsForValue().set(hasMoreKey, String.valueOf(hasMore), pageTtl);

            // L0: item fragments
            for (FeedItemResponse item : items) {
                String itemKey = "feed:item:" + item.id();
                redis.opsForValue().set(itemKey, objectMapper.writeValueAsString(item), frTtl);
            }
        } catch (Exception e) {
            log.warn("Failed to write feed cache: {}", e.getMessage());
        }
    }

    /**
     * Dynamically extends the Redis page TTL for hot pages.
     * Hotter pages stay cached longer, reducing database load during traffic spikes.
     */
    private void maybeExtendTtl(String cacheKey) {
        HotKeyDetector.Level level = hotKey.level(cacheKey);
        if (level == HotKeyDetector.Level.NONE) return;
        int extend = hotKey.extendSeconds(level);
        try {
            // Extend L1 page keys proportional to hotness
            var keys = redis.keys("feed:public:ids:*");
            if (keys != null) {
                for (var k : keys) {
                    Long ttl = redis.getExpire(k);
                    if (ttl != null && ttl > 0 && ttl < extend) {
                        redis.expire(k, Duration.ofSeconds(extend + ThreadLocalRandom.current().nextInt(10)));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("TTL extension failed: {}", e.getMessage());
        }
    }

    /** Enriches items with per-user liked/faved state without polluting the cache. */
    private FeedPageResponse enrich(FeedPageResponse cached, Long currentUserId) {
        if (currentUserId == null || cached.items() == null) return cached;
        List<FeedItemResponse> enriched = new ArrayList<>(cached.items().size());
        for (FeedItemResponse item : cached.items()) {
            boolean liked = counterService.isLiked("knowpost", item.id(), currentUserId);
            boolean faved = counterService.isFaved("knowpost", item.id(), currentUserId);
            enriched.add(new FeedItemResponse(
                    item.id(), item.title(), item.description(), item.coverImage(),
                    item.tags(), item.authorAvatar(), item.authorNickname(),
                    item.tagJson(), item.likeCount(), item.favoriteCount(),
                    liked, faved, item.isTop()));
        }
        return new FeedPageResponse(enriched, cached.page(), cached.size(), cached.hasMore());
    }

    private FeedItemResponse toFeedItem(KnowPostFeedRow r, Long currentUserId) {
        List<String> imgs = parseArray(r.getImgUrls());
        long uid = currentUserId != null ? currentUserId : 0L;
        boolean liked = currentUserId != null && counterService.isLiked("knowpost", r.getId(), uid);
        boolean faved = currentUserId != null && counterService.isFaved("knowpost", r.getId(), uid);
        Map<String, Long> counts = counterService.getCounts("knowpost", r.getId(), List.of("like", "fav"));

        return new FeedItemResponse(
                r.getId(), r.getTitle(), r.getDescription(), imgs.isEmpty() ? null : imgs.getFirst(),
                parseArray(r.getTags()), r.getAuthorAvatar(), r.getAuthorNickname(),
                r.getAuthorTagJson(), counts.getOrDefault("like", 0L), counts.getOrDefault("fav", 0L),
                liked, faved, r.getIsTop());
    }

    private static List<String> parseArray(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) return List.of();
        if (json.startsWith("[")) {
            try {
                return new ObjectMapper().readValue(json, List.class);
            } catch (Exception e) {
                return List.of();
            }
        }
        return List.of(json);
    }
}

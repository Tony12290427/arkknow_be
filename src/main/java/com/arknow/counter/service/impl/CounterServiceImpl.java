package com.arknow.counter.service.impl;

import com.arknow.counter.event.CounterAggregationConsumer;
import com.arknow.counter.event.CounterEvent;
import com.arknow.counter.event.CounterEventProducer;
import com.arknow.counter.schema.BitmapShard;
import com.arknow.counter.schema.CounterKeys;
import com.arknow.counter.schema.CounterSchema;
import com.arknow.counter.service.CounterService;
import com.arknow.knowpost.api.dto.FeedPageResponse;
import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Full counter service implementation using Redis SDS and sharded bitmaps.
 * <p>
 * Architecture:
 * <ul>
 *   <li><b>Bitmap layer (facts)</b>: records who performed each action. Sharded so that
 *       hot content does not create a single giant Redis key. Used for idempotency
 *       (re-liking is a no-op) and as the source of truth for count rebuilds.</li>
 *   <li><b>Aggregation layer (events)</b>: each state change produces a delta event
 *       that is folded into a Redis Hash bucket, then periodically batch-flushed
 *       to the SDS counter.</li>
 *   <li><b>SDS layer (counts)</b>: fixed-size binary blob, one per entity, holding
 *       the committed count for each metric. Compact, CPU-friendly, single key.</li>
 * </ul>
 * <p>
 * When the SDS is missing or corrupt, counts are rebuilt from the bitmap facts —
 * the system is self-healing.
 */
@Service
public class CounterServiceImpl implements CounterService {
    private static final Logger log = LoggerFactory.getLogger(CounterServiceImpl.class);

    private final StringRedisTemplate redis;
    private final CounterEventProducer eventProducer;
    private final CounterAggregationConsumer aggregationConsumer;
    private final Cache<String, FeedPageResponse> feedPublicCache;

    /** Lua script: atomically toggles a bit and returns 1 if changed, 0 if unchanged. */
    private static final String TOGGLE_LUA = """
            local key = KEYS[1]
            local bit = tonumber(ARGV[1])
            local action = ARGV[2]
            local old = redis.call('GETBIT', key, bit)
            if action == 'add' then
                if old == 1 then return 0 end
                redis.call('SETBIT', key, bit, 1)
                return 1
            else
                if old == 0 then return 0 end
                redis.call('SETBIT', key, bit, 0)
                return 1
            end
            """;

    public CounterServiceImpl(StringRedisTemplate redis, CounterEventProducer eventProducer,
                               CounterAggregationConsumer aggregationConsumer,
                               @Qualifier("feedPublicCache") Cache<String, FeedPageResponse> feedPublicCache) {
        this.redis = redis;
        this.eventProducer = eventProducer;
        this.aggregationConsumer = aggregationConsumer;
        this.feedPublicCache = feedPublicCache;
    }

    // ==================== Actions ====================

    /** Toggles the like state. Returns true if the state changed. */
    public boolean like(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, "like", 0, true);
    }

    /** Toggles the unlike state. Returns true if the state changed. */
    public boolean unlike(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, "like", 0, false);
    }

    /** Toggles the favorite state. */
    public boolean fav(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, "fav", 1, true);
    }

    /** Removes a favorite. */
    public boolean unfav(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, "fav", 1, false);
    }

    // ==================== Queries ====================

    @Override
    public Map<String, Long> getCounts(String entityType, String entityId, List<String> metrics) {
        String sdsKey = CounterKeys.sdsKey(entityType, entityId);
        int expectedLen = CounterSchema.SCHEMA_LEN * CounterSchema.FIELD_SIZE;
        byte[] raw = getRaw(sdsKey);
        boolean needRebuild = (raw == null || raw.length != expectedLen);

        Map<String, Long> result = new LinkedHashMap<>();
        if (needRebuild) {
            return rebuildAndGet(entityType, entityId, metrics);
        }

        // Read pending deltas from aggregation bucket (not yet flushed to SDS)
        String aggKey = CounterKeys.aggKey(entityType, entityId);
        Map<Object, Object> aggData = null;
        try {
            aggData = redis.opsForHash().entries(aggKey);
        } catch (Exception ignored) {}

        for (String m : metrics) {
            Integer idx = CounterSchema.NAME_TO_IDX.get(m);
            if (idx == null) { result.put(m, 0L); continue; }
            int off = idx * CounterSchema.FIELD_SIZE;
            long sdsVal = readInt32BE(raw, off);

            // Add pending delta from aggregation bucket
            long pending = 0L;
            if (aggData != null) {
                Object deltaObj = aggData.get(String.valueOf(idx));
                if (deltaObj != null) {
                    try { pending = Long.parseLong(String.valueOf(deltaObj)); } catch (NumberFormatException ignored) {}
                }
            }
            result.put(m, Math.max(0, sdsVal + pending));
        }
        return result;
    }

    @Override
    public Map<String, Map<String, Long>> batchGetCounts(String entityType, List<String> entityIds, List<String> metrics) {
        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        int expectedLen = CounterSchema.SCHEMA_LEN * CounterSchema.FIELD_SIZE;

        List<Object> rawResults = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (String eid : entityIds) {
                connection.stringCommands().get(CounterKeys.sdsKey(entityType, eid).getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });

        for (int i = 0; i < entityIds.size(); i++) {
            String eid = entityIds.get(i);
            Map<String, Long> counts = new LinkedHashMap<>();
            Object obj = rawResults != null && i < rawResults.size() ? rawResults.get(i) : null;
            byte[] raw = (obj instanceof String s) ? s.getBytes(StandardCharsets.UTF_8) : null;
            boolean needRebuild = (raw == null || raw.length != expectedLen);

            if (needRebuild) {
                // Use single-get rebuild (async rebuild would be better at scale)
                counts = getCounts(entityType, eid, metrics);
            } else {
                Map<Object, Object> aggData = null;
                try { aggData = redis.opsForHash().entries(CounterKeys.aggKey(entityType, eid)); } catch (Exception ignored) {}
                for (String m : metrics) {
                    Integer idx = CounterSchema.NAME_TO_IDX.get(m);
                    if (idx == null) { counts.put(m, 0L); continue; }
                    long sdsVal = readInt32BE(raw, idx * CounterSchema.FIELD_SIZE);
                    long pending = 0L;
                    if (aggData != null) {
                        Object deltaObj = aggData.get(String.valueOf(idx));
                        if (deltaObj != null) {
                            try { pending = Long.parseLong(String.valueOf(deltaObj)); } catch (NumberFormatException ignored) {}
                        }
                    }
                    counts.put(m, Math.max(0, sdsVal + pending));
                }
            }
            result.put(eid, counts);
        }
        return result;
    }

    @Override
    public boolean isLiked(String entityType, String entityId, long userId) {
        return getBit(CounterKeys.bitmapKey("like", entityType, entityId, BitmapShard.chunkOf(userId)),
                BitmapShard.bitOf(userId));
    }

    @Override
    public boolean isFaved(String entityType, String entityId, long userId) {
        return getBit(CounterKeys.bitmapKey("fav", entityType, entityId, BitmapShard.chunkOf(userId)),
                BitmapShard.bitOf(userId));
    }

    // ==================== Internals ====================

    /**
     * Atomically toggles a bit in the bitmap shard.
     * Only when the bit actually changes state (0→1 for like, 1→0 for unlike)
     * do we emit a counter event. This guarantees idempotency.
     */
    private boolean toggle(String etype, String eid, long uid, String metric, int idx, boolean add) {
        long chunk = BitmapShard.chunkOf(uid);
        long bit = BitmapShard.bitOf(uid);
        String bmKey = CounterKeys.bitmapKey(metric, etype, eid, chunk);
        DefaultRedisScript<Long> toggleScript = new DefaultRedisScript<>(TOGGLE_LUA, Long.class);
        Long changed = redis.execute(toggleScript, List.of(bmKey), String.valueOf(bit), add ? "add" : "remove");
        boolean ok = changed != null && changed == 1L;
        if (ok) {
            int delta = add ? 1 : -1;
            CounterEvent event = CounterEvent.of(etype, eid, metric, idx, uid, delta);
            eventProducer.publish(event);
            // Synchronous aggregation in MVP mode
            try { aggregationConsumer.onEvent(event); } catch (Exception ignored) {}
            // Precise cache invalidation: clear L0 fragment + affected Caffeine pages
            // L0 still holds likeCount/favCount (frozen), must delete so next read
            // assembles from SDS batchGetCounts with live values
            try {
                redis.delete("feed:item:" + eid);
                for (var entry : feedPublicCache.asMap().entrySet()) {
                    boolean hasItem = entry.getValue() != null
                        && entry.getValue().items() != null
                        && entry.getValue().items().stream()
                            .anyMatch(item -> eid.equals(item.id()));
                    if (hasItem) feedPublicCache.invalidate(entry.getKey());
                }
            } catch (Exception ignored) {}
        }
        return ok;
    }

    /** Reads a single bit from a Redis bitmap key. */
    private boolean getBit(String key, long offset) {
        Boolean bit = redis.execute((RedisCallback<Boolean>) connection ->
                connection.stringCommands().getBit(key.getBytes(StandardCharsets.UTF_8), offset));
        return Boolean.TRUE.equals(bit);
    }

    /** Reads the raw SDS byte array from Redis. */
    private byte[] getRaw(String key) {
        return redis.execute((RedisCallback<byte[]>) connection ->
                connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8)));
    }

    /** Rebuilds SDS counts from bitmap facts (BITCOUNT on all shards) and returns the counts. */
    private Map<String, Long> rebuildAndGet(String etype, String eid, List<String> metrics) {
        // Distributed lock to prevent concurrent rebuilds
        String lockKey = "lock:sds-rebuild:" + etype + ":" + eid;
        String token = UUID.randomUUID().toString();
        boolean locked = tryLock(lockKey, token, 5000L);

        int expectedLen = CounterSchema.SCHEMA_LEN * CounterSchema.FIELD_SIZE;
        byte[] newSds = new byte[expectedLen];
        Map<String, Long> result = new LinkedHashMap<>();

        try {
            for (String m : metrics) {
                Integer idx = CounterSchema.NAME_TO_IDX.get(m);
                if (idx == null) { result.put(m, 0L); continue; }
                long sum = bitCountShards(m, etype, eid);
                writeInt32BE(newSds, idx * CounterSchema.FIELD_SIZE, sum);
                result.put(m, sum);
            }
            if (locked) {
                redis.opsForValue().getOperations().delete(redis.keys(CounterKeys.aggKey(etype, eid)));
                setRaw(CounterKeys.sdsKey(etype, eid), newSds);
            }
        } finally {
            if (locked) unlock(lockKey, token);
        }
        return result;
    }

    /** Pipelined BITCOUNT across all bitmap shards for a given metric + entity. Uses SCAN to avoid blocking Redis. */
    private long bitCountShards(String metric, String etype, String eid) {
        String pattern = String.format("bm:%s:%s:%s:*", metric, etype, eid);
        Set<String> keys = scanKeys(pattern);
        if (keys == null || keys.isEmpty()) return 0L;

        List<Object> res = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (String k : keys) {
                connection.stringCommands().bitCount(k.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
        long sum = 0L;
        for (Object o : res) {
            if (o instanceof Number n) sum += n.longValue();
        }
        return sum;
    }

    /** Non-blocking SCAN-based key lookup. Never use KEYS in production. */
    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        try {
            redis.execute((RedisCallback<Void>) connection -> {
                var scanOpts = org.springframework.data.redis.core.ScanOptions.scanOptions()
                        .match(pattern).count(100).build();
                try (var c = connection.keyCommands().scan(scanOpts)) {
                    while (c.hasNext()) {
                        keys.add(new String(c.next(), StandardCharsets.UTF_8));
                    }
                } catch (Exception ignored) {}
                return null;
            });
        } catch (Exception ignored) {}
        return keys;
    }

    /** Writes raw bytes to a Redis key. */
    private void setRaw(String key, byte[] data) {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.stringCommands().set(key.getBytes(StandardCharsets.UTF_8), data);
            return null;
        });
    }

    /** Writes a 32-bit big-endian integer into a byte array at the given offset. */
    private static void writeInt32BE(byte[] buf, int offset, long val) {
        buf[offset]     = (byte) ((val >> 24) & 0xFF);
        buf[offset + 1] = (byte) ((val >> 16) & 0xFF);
        buf[offset + 2] = (byte) ((val >> 8) & 0xFF);
        buf[offset + 3] = (byte) (val & 0xFF);
    }

    /** Reads a 32-bit big-endian integer from a byte array. */
    private static long readInt32BE(byte[] buf, int offset) {
        long val = ((long) (buf[offset] & 0xFF) << 24)
                 | ((long) (buf[offset + 1] & 0xFF) << 16)
                 | ((long) (buf[offset + 2] & 0xFF) << 8)
                 | (buf[offset + 3] & 0xFF);
        // Sign-extend from 32-bit
        if (val >= 0x80000000L) val -= 0x100000000L;
        return val;
    }

    /** Distributed lock via SET NX EX. */
    private boolean tryLock(String key, String token, long ttlMillis) {
        Boolean ok = redis.execute((RedisCallback<Boolean>) connection ->
                connection.stringCommands().set(
                        key.getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8),
                        Expiration.milliseconds(ttlMillis),
                        RedisStringCommands.SetOption.SET_IF_ABSENT));
        return Boolean.TRUE.equals(ok);
    }

    /** Releases a distributed lock. Only the holder can release it (not implemented fully — just DEL). */
    private void unlock(String key, String token) {
        redis.delete(key);
    }
}

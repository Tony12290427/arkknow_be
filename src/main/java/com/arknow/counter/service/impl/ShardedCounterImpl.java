package com.arknow.counter.service.impl;

import com.arknow.counter.schema.CounterBucketConfig;
import com.arknow.counter.schema.CounterKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Sharded counter service using Redis Hash buckets.
 * <p>
 * Writes distribute across N buckets via hash(userId) % N (HINCRBY per bucket).
 * Reads SUM all bucket fields. Degrades to DB atomic UPDATE when Redis is down.
 */
@Service
public class ShardedCounterImpl {
    private static final Logger log = LoggerFactory.getLogger(ShardedCounterImpl.class);

    /** Lua: atomically reads and deletes a Redis Hash field, returning its value. */
    private static final String DRAIN_LUA = """
            local key = KEYS[1]
            local field = ARGV[1]
            local val = redis.call('HGET', key, field)
            if val then
                redis.call('HDEL', key, field)
                return val
            end
            return 0
            """;

    private final StringRedisTemplate redis;
    private final CounterBucketConfig bucketConfig;
    private final JdbcTemplate jdbc;

    public ShardedCounterImpl(StringRedisTemplate redis, CounterBucketConfig bucketConfig,
                               JdbcTemplate jdbc) {
        this.redis = redis;
        this.bucketConfig = bucketConfig;
        this.jdbc = jdbc;
    }

    /** Write: hash(userId) % N picks a bucket, then HINCRBY. */
    public void increment(String entityType, String entityId, String metric,
                           long userId, long delta) {
        int buckets = bucketConfig.getBucketCount(entityType);
        int bucketId = bucketIndex(userId, buckets);
        String key = CounterKeys.bucketKey(entityType, entityId, bucketId);
        try {
            redis.opsForHash().increment(key, metric, delta);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, degrading to DB for {}/{}/{}", entityType, entityId, metric);
            degradeIncrement(entityType, entityId, metric, delta);
        }
    }

    /** Read: SUM all buckets for a single metric. */
    public long getCount(String entityType, String entityId, String metric) {
        int buckets = bucketConfig.getBucketCount(entityType);
        long sum = 0;
        for (int i = 0; i < buckets; i++) {
            String key = CounterKeys.bucketKey(entityType, entityId, i);
            try {
                Object v = redis.opsForHash().get(key, metric);
                if (v != null) sum += Long.parseLong(String.valueOf(v));
            } catch (Exception ignored) {}
        }
        return sum;
    }

    /** Read: SUM all buckets for all metrics. */
    public Map<String, Long> getCounts(String entityType, String entityId) {
        int buckets = bucketConfig.getBucketCount(entityType);
        Map<String, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < buckets; i++) {
            String key = CounterKeys.bucketKey(entityType, entityId, i);
            try {
                Map<Object, Object> entries = redis.opsForHash().entries(key);
                for (var e : entries.entrySet()) {
                    String m = String.valueOf(e.getKey());
                    long v = Long.parseLong(String.valueOf(e.getValue()));
                    result.merge(m, v, Long::sum);
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    /**
     * Atomically drain one bucket field — reads and deletes via Lua.
     * Returns the drained value, or 0 if the field didn't exist.
     */
    public long drainBucketField(String entityType, String entityId, int bucketId, String metric) {
        String key = CounterKeys.bucketKey(entityType, entityId, bucketId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(DRAIN_LUA, Long.class);
        try {
            Long val = redis.execute(script, List.of(key), metric);
            return val != null ? val : 0L;
        } catch (Exception e) {
            log.warn("Drain failed for {}/{}/bucket:{}/{}", entityType, entityId, bucketId, metric);
            return 0L;
        }
    }

    /** Degraded write: Redis unavailable, use DB atomic UPDATE directly. */
    public void degradeIncrement(String entityType, String entityId, String metric, long delta) {
        jdbc.update(
            "INSERT INTO counter_snapshot (entity_type, entity_id, metric, count) " +
            "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE count = count + ?",
            entityType, entityId, metric, delta, delta);
    }

    /** Deterministic bucket index — avoids Integer.MIN_VALUE overflow. */
    static int bucketIndex(long userId, int numBuckets) {
        long h = userId ^ (userId >>> 16);
        return ((int) h & Integer.MAX_VALUE) % numBuckets;
    }
}

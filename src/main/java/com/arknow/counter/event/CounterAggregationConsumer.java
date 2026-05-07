package com.arknow.counter.event;

import com.arknow.counter.schema.CounterKeys;
import com.arknow.counter.schema.CounterSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates counter increment events and periodically flushes them to SDS counters.
 * <p>
 * Two-stage pipeline:
 * <ol>
 *   <li><b>Accumulate</b>: each event's delta is atomically added to a Redis Hash
 *       aggregation bucket via {@code HINCRBY}.</li>
 *   <li><b>Flush</b>: a periodic scheduled task scans active aggregation buckets,
 *       folds each field's accumulated delta into the SDS binary counter via a Lua
 *       script, then deletes the flushed field from the bucket.</li>
 * </ol>
 * <p>
 * This converts high-frequency fine-grained writes (one per user action) into
 * low-frequency batch writes (one per entity per flush cycle), reducing write
 * amplification by 10-1000x.
 */
@Component
public class CounterAggregationConsumer {
    private static final Logger log = LoggerFactory.getLogger(CounterAggregationConsumer.class);

    private final StringRedisTemplate redis;

    /** Lua script: atomically increments a field in an SDS blob at the given offset. */
    private static final String SDS_INCR_LUA = """
            local key = KEYS[1]
            local totalLen = tonumber(ARGV[1])
            local fieldSize = tonumber(ARGV[2])
            local idx = tonumber(ARGV[3])
            local delta = tonumber(ARGV[4])
            local offset = idx * fieldSize

            local raw = redis.call('GET', key)
            if not raw then
                raw = string.rep('\\0', totalLen * fieldSize)
            end
            if #raw ~= totalLen * fieldSize then
                raw = string.rep('\\0', totalLen * fieldSize)
            end

            local val = 0
            for i = 1, fieldSize do
                val = val * 256 + string.byte(raw, offset + i)
            end
            -- sign-extend from 32-bit
            if val >= 2147483648 then val = val - 4294967296 end
            val = val + delta
            -- non-negative clamp
            if val < 0 then val = 0 end

            -- Build big-endian bytes (MSB first)
            local bytes = {}
            for i = 0, fieldSize - 1 do
                bytes[i + 1] = string.char(math.floor(val / (256 ^ (fieldSize - 1 - i))) % 256)
            end
            local patch = table.concat(bytes)

            local before = raw:sub(1, offset)
            local after = raw:sub(offset + fieldSize + 1)
            redis.call('SET', key, before .. patch .. after)
            return val
            """;

    public CounterAggregationConsumer(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Processes a counter event by accumulating the delta into the aggregation bucket.
     * Called synchronously in the MVP (without Kafka), or by Kafka consumer in production.
     */
    public void onEvent(CounterEvent evt) {
        String aggKey = CounterKeys.aggKey(evt.entityType(), evt.entityId());
        String field = String.valueOf(evt.idx());
        try {
            redis.opsForHash().increment(aggKey, field, evt.delta());
        } catch (Exception e) {
            log.warn("Failed to accumulate counter event: {}", e.getMessage());
        }
    }

    /**
     * Scheduled task that scans aggregation buckets and atomically folds their
     * accumulated deltas into the corresponding SDS counters.
     * <p>
     * Runs every 1 second for near-real-time counter visibility.
     * After flushing, empty hash fields are deleted and empty buckets are removed.
     */
    @Scheduled(fixedDelay = 1000)
    public void flush() {
        Set<String> keys = scanKeys("agg:" + CounterSchema.SCHEMA_ID + ":*");
        if (keys == null || keys.isEmpty()) return;

        DefaultRedisScript<Long> incrScript = new DefaultRedisScript<>(SDS_INCR_LUA, Long.class);

        for (String aggKey : keys) {
            Map<Object, Object> entries = redis.opsForHash().entries(aggKey);
            if (entries.isEmpty()) {
                redis.delete(aggKey);
                continue;
            }

            String[] parts = aggKey.split(":", 4);
            if (parts.length < 4) continue;
            String cntKey = CounterKeys.sdsKey(parts[2], parts[3]);

            for (Map.Entry<Object, Object> e : entries.entrySet()) {
                int idx;
                long delta;
                try {
                    idx = Integer.parseInt(String.valueOf(e.getKey()));
                    delta = Long.parseLong(String.valueOf(e.getValue()));
                } catch (NumberFormatException nfe) {
                    continue;
                }
                if (delta == 0) continue;

                try {
                    redis.execute(incrScript, List.of(cntKey),
                            String.valueOf(CounterSchema.SCHEMA_LEN),
                            String.valueOf(CounterSchema.FIELD_SIZE),
                            String.valueOf(idx),
                            String.valueOf(delta));
                    redis.opsForHash().delete(aggKey, String.valueOf(e.getKey()));
                } catch (Exception ex) {
                    // Retry next flush cycle
                }
            }

            Long size = redis.opsForHash().size(aggKey);
            if (size == null || size == 0) {
                redis.delete(aggKey);
            }
        }
    }

    /** Non-blocking SCAN-based key lookup. Never use KEYS in production. */
    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            var scanOpts = org.springframework.data.redis.core.ScanOptions.scanOptions()
                    .match(pattern).count(100).build();
            try (var c = connection.keyCommands().scan(scanOpts)) {
                while (c.hasNext()) {
                    keys.add(new String(c.next(), StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {}
            return null;
        });
        return keys;
    }
}

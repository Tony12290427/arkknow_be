package com.arknow.counter.service.impl;

import com.arknow.counter.schema.CounterBucketConfig;
import com.arknow.counter.schema.CounterKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Periodically drains sharded Redis counter buckets and batch-flushes deltas to MySQL.
 * <p>
 * Runs every 1 second. Each cycle: snapshot dirty entities → drain all buckets per entity
 * via atomic Lua GET+DEL → batch INSERT ... ON DUPLICATE KEY UPDATE.
 */
@Component
@EnableScheduling
public class CounterFlushConsumer {
    private static final Logger log = LoggerFactory.getLogger(CounterFlushConsumer.class);

    private final ShardedCounterImpl shardedCounter;
    private final CounterBucketConfig bucketConfig;
    private final JdbcTemplate jdbc;
    private final Set<String> dirtyEntities = Collections.synchronizedSet(new HashSet<>());

    public CounterFlushConsumer(ShardedCounterImpl shardedCounter, CounterBucketConfig bucketConfig,
                                 JdbcTemplate jdbc) {
        this.shardedCounter = shardedCounter;
        this.bucketConfig = bucketConfig;
        this.jdbc = jdbc;
    }

    /** Mark an entity as having pending deltas that need flushing to DB. */
    public void markDirty(String entityType, String entityId) {
        dirtyEntities.add(entityType + ":" + entityId);
    }

    /** Scheduled task: drain all dirty entities' buckets and batch UPDATE MySQL. */
    @Scheduled(fixedDelay = 1000)
    public void flushToDb() {
        if (dirtyEntities.isEmpty()) return;
        Set<String> snapshot;
        synchronized (dirtyEntities) {
            snapshot = new HashSet<>(dirtyEntities);
            dirtyEntities.clear();
        }

        for (String key : snapshot) {
            String[] parts = key.split(":", 2);
            if (parts.length < 2) continue;
            String entityType = parts[0];
            String entityId = parts[1];

            try {
                int buckets = bucketConfig.getBucketCount(entityType);
                for (int i = 0; i < buckets; i++) {
                    // Drain each known metric field from this bucket
                    for (String metric : new String[]{"like", "fav", "comment", "view", "followings", "followers"}) {
                        long delta = shardedCounter.drainBucketField(entityType, entityId, i, metric);
                        if (delta != 0) {
                            jdbc.update(
                                "INSERT INTO counter_snapshot (entity_type, entity_id, metric, count) " +
                                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE count = count + ?",
                                entityType, entityId, metric, delta, delta);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Flush failed for {}/{}: {}", entityType, entityId, e.getMessage());
                // Re-mark dirty for retry next cycle
                dirtyEntities.add(key);
            }
        }
    }
}

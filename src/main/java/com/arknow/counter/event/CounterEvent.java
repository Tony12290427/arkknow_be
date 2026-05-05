package com.arknow.counter.event;

/**
 * Immutable record representing a single counter increment/decrement.
 * <p>
 * Produced when a user performs an action (like, unlike, fav, unfav) and consumed
 * asynchronously via Kafka to aggregate into Redis buckets before batch-flushing
 * to the SDS fixed-structure counters.
 */
public record CounterEvent(
        String entityType,
        String entityId,
        String metric,
        int idx,
        long userId,
        int delta
) {
    public static CounterEvent of(String entityType, String entityId, String metric, int idx, long userId, int delta) {
        return new CounterEvent(entityType, entityId, metric, idx, userId, delta);
    }
}

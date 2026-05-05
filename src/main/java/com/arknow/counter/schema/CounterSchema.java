package com.arknow.counter.schema;

import java.util.Map;

/**
 * Central registry for counter field layout in the SDS binary structure.
 * <p>
 * Instead of storing each metric as a separate Redis key or Hash field, all counters
 * for one entity are packed into a single fixed-length byte array. Each metric occupies
 * {@value #FIELD_SIZE} bytes at a pre-defined offset.
 * <p>
 * This eliminates per-field key overhead and avoids Redis Hash metadata, significantly
 * reducing memory at scale (millions of entities).
 * <p>
 * Layout (each field = 4 bytes, big-endian int32):
 * <pre>
 * Offset | Metric (entity)    | Metric (user)
 * 0      | like              | followings
 * 4      | fav               | followers
 * 8      | (reserved)        | posts
 * 12     | (reserved)        | likedPosts
 * 16     | (reserved)        | favedPosts
 * </pre>
 */
public final class CounterSchema {
    /** Schema version for future migrations. */
    public static final int SCHEMA_ID = 1;
    /** Total number of metrics per entity counter. */
    public static final int SCHEMA_LEN = 5;
    /** Bytes per metric field (32-bit signed integer, big-endian). */
    public static final int FIELD_SIZE = 4;

    /** Metric name to index mapping for entity-level counters. */
    public static final Map<String, Integer> NAME_TO_IDX = Map.of(
            "like", 0,
            "fav", 1
    );

    /** Metric name to index mapping for user-level counters. */
    public static final Map<String, Integer> USER_NAME_TO_IDX = Map.of(
            "followings", 0,
            "followers", 1,
            "posts", 2,
            "likedPosts", 3,
            "favedPosts", 4
    );

    private CounterSchema() {}
}

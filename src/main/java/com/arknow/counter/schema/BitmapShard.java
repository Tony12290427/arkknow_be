package com.arknow.counter.schema;

/**
 * Bitmap sharding configuration.
 * <p>
 * Each content entity's user-state bitmap is split into fixed-size shards (32K bits = 4KB).
 * This avoids single-key hotspots on popular content: millions of users marking "liked"
 * on a viral post are distributed across many Redis keys rather than hammering one.
 * <p>
 * Shard index and bit offset are derived from the user ID:
 * <ul>
 *   <li>{@code chunkOf(userId) = userId / CHUNK_SIZE}</li>
 *   <li>{@code bitOf(userId) = userId % CHUNK_SIZE}</li>
 * </ul>
 */
public final class BitmapShard {
    /** 32K bits per shard = 4KB per Redis key. */
    public static final int CHUNK_SIZE = 32_768;

    /** Determines which shard a user belongs to. */
    public static long chunkOf(long userId) {
        return userId / CHUNK_SIZE;
    }

    /** Determines the bit position within the user's shard. */
    public static long bitOf(long userId) {
        return userId % CHUNK_SIZE;
    }

    private BitmapShard() {}
}

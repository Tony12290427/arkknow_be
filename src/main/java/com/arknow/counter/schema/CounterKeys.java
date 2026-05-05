package com.arknow.counter.schema;

/**
 * Redis key naming conventions for the counter system.
 * <p>
 * Key patterns:
 * <ul>
 *   <li>{@code bm:<metric>:<etype>:<eid>:<chunk>} — bitmap shard for user state</li>
 *   <li>{@code agg:<schema>:<etype>:<eid>} — Kafka aggregation bucket (Redis Hash)</li>
 *   <li>{@code sds:<etype>:<eid>} — final committed counter (SDS binary blob)</li>
 * </ul>
 */
public final class CounterKeys {

    /** Bitmap key for user-state tracking. */
    public static String bitmapKey(String metric, String etype, String eid, long chunk) {
        return String.format("bm:%s:%s:%s:%d", metric, etype, eid, chunk);
    }

    /** Aggregation bucket key. Increments are folded here before batch-flush to SDS. */
    public static String aggKey(String etype, String eid) {
        return String.format("agg:%d:%s:%s", CounterSchema.SCHEMA_ID, etype, eid);
    }

    /** SDS binary counter key — the final committed count. */
    public static String sdsKey(String etype, String eid) {
        return String.format("sds:%d:%s:%s", CounterSchema.SCHEMA_ID, etype, eid);
    }

    private CounterKeys() {}
}

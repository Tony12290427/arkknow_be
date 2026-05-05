package com.arknow.relation.outbox;

/**
 * Kafka topic constants for the Outbox event pipeline.
 * <p>
 * {@code CANAL_OUTBOX} carries row-change events captured by Canal from the outbox table's binlog.
 * Downstream consumers subscribe to this topic to build async projections.
 */
public final class OutboxTopics {
    /** Topic receiving Canal-parsed outbox table changes. */
    public static final String CANAL_OUTBOX = "canal-outbox";

    private OutboxTopics() {}
}

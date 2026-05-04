package com.arknow.knowpost.id;

import org.springframework.stereotype.Component;

/**
 * Thread-safe Snowflake distributed ID generator.
 * <p>
 * Produces globally unique, time-ordered 64-bit IDs without a central coordinator.
 * <p>
 * Bit layout: {@code [1 reserved] [41 timestamp] [5 datacenter] [5 worker] [12 sequence]}
 * <ul>
 *   <li>41-bit timestamp: custom epoch (2024-01-01), supports ~69 years</li>
 *   <li>5+5 node: up to 32 datacenters x 32 workers = 1024 nodes</li>
 *   <li>12-bit sequence: up to 4096 IDs per millisecond per node</li>
 * </ul>
 * <p>
 * Clock rollback handling: small rollbacks (≤5ms) are tolerated by sleeping until
 * the clock catches up. Larger rollbacks throw an exception to prevent duplicate IDs.
 * <p>
 * Thread safety is achieved via {@code synchronized} on {@code nextId()}. The critical
 * section is small (a few arithmetic operations), so contention is negligible for
 * typical workloads.
 */
@Component
public class SnowflakeIdGenerator {
    /** Custom epoch: 2024-01-01 00:00:00 UTC. */
    private static final long EPOCH = 1704067200000L;
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private final long datacenterId;
    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    /** Default constructor uses datacenter=1, worker=1 for single-node setups. */
    public SnowflakeIdGenerator() {
        this(1, 1);
    }

    /** Multi-node constructor. datacenterId and workerId must be within the bit ranges. */
    public SnowflakeIdGenerator(long datacenterId, long workerId) {
        if (workerId > MAX_WORKER_ID || workerId < 0)
            throw new IllegalArgumentException("workerId out of range");
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0)
            throw new IllegalArgumentException("datacenterId out of range");
        this.datacenterId = datacenterId;
        this.workerId = workerId;
    }

    /**
     * Generates the next unique ID.
     * <p>
     * When multiple IDs are generated within the same millisecond, the sequence counter
     * is incremented. If the counter overflows (4096 IDs in one millisecond), the method
     * busy-waits until the next millisecond.
     *
     * @return a globally unique, time-ordered 64-bit ID
     * @throws IllegalStateException if the system clock moves backwards more than 5ms
     */
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            // Tolerate small clock adjustments (e.g. NTP corrections)
            if (offset <= 5) {
                try {
                    Thread.sleep(offset);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted during clock catch-up", e);
                }
                timestamp = System.currentTimeMillis();
                if (timestamp < lastTimestamp)
                    throw new IllegalStateException("Clock moved backwards");
            } else {
                throw new IllegalStateException("Clock moved backwards, offset=" + offset + "ms");
            }
        }
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) timestamp = waitNextMillis(lastTimestamp);
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) timestamp = System.currentTimeMillis();
        return timestamp;
    }
}

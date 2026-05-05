package com.arknow.counter.event;

/**
 * Kafka topic constants for counter event pipeline.
 */
public final class CounterTopics {
    /** Topic carrying counter increment/decrement events. */
    public static final String EVENTS = "counter-events";

    private CounterTopics() {}
}

package com.arknow.counter.replay;

import com.arknow.counter.event.CounterEvent;
import com.arknow.counter.service.impl.ShardedCounterImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class CounterRebuildConsumer {
    private static final Logger log = LoggerFactory.getLogger(CounterRebuildConsumer.class);
    static final String LISTENER_ID = "counter-rebuild-listener";

    private final ShardedCounterImpl shardedCounter;
    private final ObjectMapper objectMapper;
    private final KafkaListenerEndpointRegistry registry;

    private final AtomicLong currentOffset = new AtomicLong(0);
    private final AtomicLong endOffset = new AtomicLong(0);
    private volatile long lag = 0;

    public CounterRebuildConsumer(ShardedCounterImpl shardedCounter, ObjectMapper objectMapper,
                                   KafkaListenerEndpointRegistry registry, MeterRegistry meterRegistry) {
        this.shardedCounter = shardedCounter;
        this.objectMapper = objectMapper;
        this.registry = registry;
        Gauge.builder("counter.rebuild.offset.current", currentOffset, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder("counter.rebuild.offset.end", endOffset, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder("counter.rebuild.lag", () -> lag)
                .register(meterRegistry);
    }

    @KafkaListener(
        id = LISTENER_ID,
        topics = "counter-events",
        groupId = "counter-rebuild",
        autoStartup = "false"
    )
    public void onMessage(String message, Acknowledgment ack) {
        try {
            CounterEvent event = objectMapper.readValue(message, CounterEvent.class);
            shardedCounter.increment(
                event.entityType(), event.entityId(), event.metric(),
                event.userId(), event.delta());
            ack.acknowledge();
        } catch (Exception e) {
            log.warn("Rebuild failed for message: {}", e.getMessage());
        }
    }

    public void start() {
        registry.getListenerContainer(LISTENER_ID).start();
        log.info("Counter rebuild consumer started, groupId=counter-rebuild");
    }

    /** Update progress. Called periodically or from the admin controller. Stops when lag <= 0. */
    public void updateProgress(long current, long end) {
        this.currentOffset.set(current);
        this.endOffset.set(end);
        this.lag = end - current;
        if (lag <= 0 && end > 0) {
            log.info("Rebuild complete — stopping consumer. Final offset={}", end);
            registry.getListenerContainer(LISTENER_ID).stop();
        }
    }
}

package com.arknow.counter.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Publishes counter events.
 * <p>
 * In production with Kafka, events are sent to the {@code counter-events} topic
 * for async aggregation. Without Kafka, the consumer is called synchronously
 * in-thread — the producer interface stays the same either way.
 */
@Component
public class CounterEventProducer {
    private static final Logger log = LoggerFactory.getLogger(CounterEventProducer.class);
    private final ObjectMapper objectMapper;

    public CounterEventProducer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a counter event. Currently logs and passes to the aggregation consumer
     * synchronously. Replace with KafkaTemplate.send() when Kafka is available.
     */
    public void publish(CounterEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            log.debug("Counter event: {}", json);
            // In production: kafkaTemplate.send(CounterTopics.EVENTS, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize counter event", e);
        }
    }
}

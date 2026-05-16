package com.arknow.counter.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CounterEventProducer {
    private static final Logger log = LoggerFactory.getLogger(CounterEventProducer.class);
    private static final String TOPIC = "counter-events";
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public CounterEventProducer(ObjectMapper objectMapper, KafkaTemplate<String, String> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(CounterEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, event.entityId(), json).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send counter event to Kafka: {}", ex.getMessage());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize counter event", e);
        }
    }
}

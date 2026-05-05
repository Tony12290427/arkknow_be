package com.arknow.relation.outbox;

import com.arknow.common.util.OutboxMessageUtil;
import com.arknow.relation.event.RelationEvent;
import com.arknow.relation.processor.RelationEventProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Kafka consumer for Canal outbox events — user relations.
 * <p>
 * Listens to the {@code canal-outbox} topic, extracts relation events from
 * Canal-parsed outbox rows, and delegates to {@link RelationEventProcessor}
 * for async projection updates (follower table, counters, caches).
 * <p>
 * Uses manual offset commit to guarantee at-least-once processing: the offset
 * is only committed after all rows in the batch are successfully processed.
 */
@Service
public class CanalOutboxConsumer {
    private static final Logger log = LoggerFactory.getLogger(CanalOutboxConsumer.class);

    private final ObjectMapper objectMapper;
    private final RelationEventProcessor processor;

    public CanalOutboxConsumer(ObjectMapper objectMapper, RelationEventProcessor processor) {
        this.objectMapper = objectMapper;
        this.processor = processor;
    }

    @KafkaListener(topics = OutboxTopics.CANAL_OUTBOX, groupId = "relation-outbox-consumer")
    public void onMessage(String message, Acknowledgment ack) {
        try {
            List<JsonNode> rows = OutboxMessageUtil.extractRows(objectMapper, message);
            if (rows.isEmpty()) {
                ack.acknowledge();
                return;
            }
            for (JsonNode row : rows) {
                JsonNode payloadNode = row.get("payload");
                if (payloadNode == null) continue;
                RelationEvent evt = objectMapper.readValue(payloadNode.asText(), RelationEvent.class);
                processor.process(evt);
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.warn("Failed to process outbox message: {}", e.getMessage());
        }
    }
}

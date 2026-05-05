package com.arknow.search.outbox;

import com.arknow.common.util.OutboxMessageUtil;
import com.arknow.search.index.SearchIndexService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Kafka consumer for Canal outbox events — search index sync.
 * <p>
 * Listens to the {@code canal-outbox} topic and keeps the Elasticsearch
 * search index in sync with knowledge post changes. When a post is published
 * or deleted, the corresponding ES document is upserted or soft-deleted.
 */
@Service
public class CanalOutboxConsumerSearch {
    private static final Logger log = LoggerFactory.getLogger(CanalOutboxConsumerSearch.class);

    private final ObjectMapper objectMapper;
    private final SearchIndexService indexService;

    public CanalOutboxConsumerSearch(ObjectMapper objectMapper, SearchIndexService indexService) {
        this.objectMapper = objectMapper;
        this.indexService = indexService;
    }

    @KafkaListener(topics = "canal-outbox", groupId = "search-index-consumer")
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
                JsonNode payload = objectMapper.readTree(payloadNode.asText());
                String entity = text(payload.get("entity"));
                String op = text(payload.get("op"));
                Long id = asLong(payload.get("id"));
                if (!"knowpost".equals(entity) || id == null) continue;

                if ("delete".equalsIgnoreCase(op)) {
                    indexService.softDeleteKnowPost(id);
                } else {
                    indexService.upsertKnowPost(id);
                }
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.warn("Failed to process search outbox: {}", e.getMessage());
        }
    }

    private String text(JsonNode n) { return n == null ? null : n.asText(); }

    private Long asLong(JsonNode n) {
        if (n == null) return null;
        try { return Long.parseLong(n.asText()); } catch (Exception e) { return null; }
    }
}

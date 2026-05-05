package com.arknow.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility for extracting outbox table rows from Canal binlog JSON messages.
 * <p>
 * Canal publishes MySQL binlog changes as JSON. This utility parses that JSON
 * and filters for INSERT/UPDATE events on the outbox table, returning only the
 * relevant row data for downstream consumers.
 */
public final class OutboxMessageUtil {
    private OutboxMessageUtil() {}

    /**
     * Extracts outbox table rows from a Canal JSON message.
     * <p>
     * Only processes messages where:
     * <ul>
     *   <li>{@code table} equals "outbox"</li>
     *   <li>{@code type} is INSERT or UPDATE</li>
     *   <li>{@code data} is a non-empty array</li>
     * </ul>
     *
     * @param objectMapper Jackson parser
     * @param message      Canal JSON message string
     * @return list of row nodes, or empty list if not applicable
     */
    public static List<JsonNode> extractRows(ObjectMapper objectMapper, String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode table = root.get("table");
            if (table == null || !"outbox".equals(table.asText())) {
                return Collections.emptyList();
            }
            JsonNode type = root.get("type");
            if (type == null || (!"INSERT".equals(type.asText()) && !"UPDATE".equals(type.asText()))) {
                return Collections.emptyList();
            }
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                return Collections.emptyList();
            }
            List<JsonNode> rows = new ArrayList<>();
            data.forEach(rows::add);
            return rows;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}

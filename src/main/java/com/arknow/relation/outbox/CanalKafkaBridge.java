package com.arknow.relation.outbox;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.task.TaskExecutor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;

/**
 * Canal-to-Kafka bridge that tails MySQL binlog and publishes outbox changes.
 * <p>
 * Subscribes to the outbox table's binlog via Canal, extracts INSERT/UPDATE
 * row data, and publishes the payload field to the {@code canal-outbox} Kafka topic.
 * <p>
 * Only activated when {@code canal.enabled=true}. Uses {@code SmartLifecycle}
 * to start after the application context is ready and stop gracefully on shutdown.
 */
@Service
public class CanalKafkaBridge implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(CanalKafkaBridge.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;
    private final boolean enabled;
    private final String host;
    private final int port;
    private final String destination;
    private final String username;
    private final String password;
    private final String filter;
    private final int batchSize;
    private final long intervalMs;
    private volatile boolean running;
    private CanalConnector connector;

    public CanalKafkaBridge(KafkaTemplate<String, String> kafka,
                            ObjectMapper objectMapper,
                            @Qualifier("taskExecutor") TaskExecutor taskExecutor,
                            @Value("${canal.enabled}") boolean enabled,
                            @Value("${canal.host}") String host,
                            @Value("${canal.port}") int port,
                            @Value("${canal.destination}") String destination,
                            @Value("${canal.username}") String username,
                            @Value("${canal.password}") String password,
                            @Value("${canal.filter}") String filter,
                            @Value("${canal.batchSize}") int batchSize,
                            @Value("${canal.intervalMs}") long intervalMs) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.destination = destination;
        this.username = username;
        this.password = password;
        this.filter = filter;
        this.batchSize = batchSize;
        this.intervalMs = intervalMs;
    }

    @Override
    public void start() {
        if (!enabled || running) return;
        running = true;
        taskExecutor.execute(() -> {
            try {
                connector = CanalConnectors.newSingleConnector(
                        new InetSocketAddress(host, port), destination, username, password);
                connector.connect();
                connector.subscribe(filter);
                connector.rollback();
                log.info("Canal bridge started: {}:{} dest={}", host, port, destination);

                while (running) {
                    Message message = connector.getWithoutAck(batchSize);
                    long batchId = message.getId();
                    if (batchId == -1 || message.getEntries() == null || message.getEntries().isEmpty()) {
                        try { Thread.sleep(intervalMs); } catch (InterruptedException ignored) {}
                        continue;
                    }
                    for (CanalEntry.Entry entry : message.getEntries()) {
                        if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) continue;
                        CanalEntry.RowChange rowChange;
                        try {
                            rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
                        } catch (Exception e) { continue; }

                        CanalEntry.EventType eventType = rowChange.getEventType();
                        if (eventType != CanalEntry.EventType.INSERT && eventType != CanalEntry.EventType.UPDATE) continue;

                        ArrayNode dataArray = objectMapper.createArrayNode();
                        for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                            ObjectNode rowNode = objectMapper.createObjectNode();
                            for (CanalEntry.Column col : rowData.getAfterColumnsList()) {
                                if ("payload".equalsIgnoreCase(col.getName())) {
                                    rowNode.put("payload", col.getValue());
                                }
                            }
                            dataArray.add(rowNode);
                        }
                        ObjectNode msgNode = objectMapper.createObjectNode();
                        msgNode.put("table", entry.getHeader().getTableName());
                        msgNode.put("type", eventType == CanalEntry.EventType.INSERT ? "INSERT" : "UPDATE");
                        msgNode.set("data", dataArray);
                        try {
                            kafka.send(OutboxTopics.CANAL_OUTBOX, objectMapper.writeValueAsString(msgNode));
                        } catch (Exception ignored) {}
                    }
                    connector.ack(batchId);
                }
            } catch (Exception e) {
                log.error("Canal bridge error", e);
            } finally {
                if (connector != null) {
                    try { connector.disconnect(); } catch (Exception ex) {}
                }
            }
        });
    }

    @Override
    public void stop() { running = false; }

    @Override
    public boolean isRunning() { return running; }
}

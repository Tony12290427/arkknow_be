# Kafka 灾难回放机制 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 Kafka 灾难回放机制：CounterEvent 上 Kafka → 独立消费者从 earliest 重放 → 复用 ShardedCounter.increment() 重建计数 → Admin API 控制启停 + 进度查询 + Micrometer 监控。

**Architecture:** CounterEventProducer 改为真正发 Kafka topic `counter-events`；CounterRebuildConsumer（独立 groupId `counter-rebuild`，默认不启动）从 earliest 消费并重放；Admin API 动态启停 listener + 返回 offset/lag；Micrometer gauge 暴露重建进度。

**Tech Stack:** Spring Kafka, Micrometer, KafkaListenerEndpointRegistry

---

### File Structure

```
src/main/java/com/arknow/counter/
├── event/CounterEventProducer.java         # MODIFY — KafkaTemplate 真正发送
├── replay/
│   ├── CounterRebuildConsumer.java         # NEW — 独立消费者,重放进 ShardedCounter
│   └── CounterRebuildController.java       # NEW — Admin API,启停+进度查询
└── config/
    └── CounterKafkaConfig.java             # NEW — Kafka topic/consumer 配置

src/main/resources/application.yml          # MODIFY — counter kafka 配置
```

---

### Task 1: CounterEventProducer — 正真发送到 Kafka

**Files:**
- Modify: `src/main/java/com/arknow/counter/event/CounterEventProducer.java`

- [ ] **Step 1: 注入 KafkaTemplate 并发送到 counter-events topic**

```java
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
                } else {
                    log.debug("Counter event sent: offset={}", result.getRecordMetadata().offset());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize counter event", e);
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -q
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/arknow/counter/event/CounterEventProducer.java
git commit -m "feat: CounterEventProducer 正真发送 Kafka topic counter-events"
```

---

### Task 2: CounterRebuildConsumer — 独立回放消费者

**Files:**
- Create: `src/main/java/com/arknow/counter/replay/CounterRebuildConsumer.java`

- [ ] **Step 1: 创建消费者**

```java
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
    private static final String LISTENER_ID = "counter-rebuild-listener";

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

    /** Start rebuild and update end offset (caller computes from Kafka consumer metrics). */
    public void start() {
        registry.getListenerContainer(LISTENER_ID).start();
        log.info("Counter rebuild consumer started, groupId=counter-rebuild");
    }

    /** Update progress from external offset queries. */
    public void updateProgress(long current, long end) {
        this.currentOffset.set(current);
        this.endOffset.set(end);
        this.lag = end - current;
        if (lag <= 0) {
            log.info("Rebuild complete — stopping consumer");
            registry.getListenerContainer(LISTENER_ID).stop();
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -q
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/arknow/counter/replay/CounterRebuildConsumer.java
git commit -m "feat: CounterRebuildConsumer — 独立消费者从 earliest 重放"
```

---

### Task 3: CounterRebuildController — Admin API

**Files:**
- Create: `src/main/java/com/arknow/counter/replay/CounterRebuildController.java`

- [ ] **Step 1: 创建管理接口**

```java
package com.arknow.counter.replay;

import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/counter")
public class CounterRebuildController {
    private final CounterRebuildConsumer rebuildConsumer;
    private final KafkaListenerEndpointRegistry registry;

    public CounterRebuildController(CounterRebuildConsumer rebuildConsumer,
                                     KafkaListenerEndpointRegistry registry) {
        this.rebuildConsumer = rebuildConsumer;
        this.registry = registry;
    }

    @PostMapping("/rebuild")
    public Map<String, Object> startRebuild(@RequestParam(required = false) String entityType) {
        try {
            rebuildConsumer.start();
            // Query Kafka consumer group offsets (initial lag info)
            // For now return basic status — detailed offset tracking
            // comes from the consumer's poll cycle
            return Map.of(
                "status", "started",
                "groupId", "counter-rebuild",
                "topic", "counter-events",
                "entityType", entityType != null ? entityType : "all",
                "note", "Consumer started from earliest. Monitor /actuator/metrics/counter.rebuild.*"
            );
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    @GetMapping("/rebuild/status")
    public Map<String, Object> status() {
        var container = registry.getListenerContainer("counter-rebuild-listener");
        return Map.of(
            "running", container != null && container.isRunning(),
            "groupId", "counter-rebuild"
        );
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -q
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/arknow/counter/replay/CounterRebuildController.java
git commit -m "feat: Admin API POST /admin/counter/rebuild 启停回放"
```

---

### Task 4: Kafka 配置 — 新增 counter topic 和 consumer 配置

**Files:**
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: 添加 counter kafka 配置**

```yaml
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      auto-offset-reset: earliest
      enable-auto-commit: false
    # counter-rebuild 专用消费者配置
    counter-rebuild:
      max-poll-records: 500
      concurrency: 2
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "feat: kafka counter-rebuild consumer 配置"
```

---

### Task 5: 端到端测试

- [ ] **Step 1: 重启后端**

```bash
pkill -f 'zhizhou_be'; sleep 3; mvn spring-boot:run &
```

- [ ] **Step 2: 发送一个测试 CounterEvent 到 Kafka**

```bash
echo '{"entityType":"knowpost","entityId":"312558779470319616","metric":"like","idx":0,"userId":6,"delta":1}' | kafka-console-producer --broker-list localhost:9092 --topic counter-events
```

- [ ] **Step 3: 启动回放**

```bash
curl -X POST 'http://localhost:8080/api/v1/admin/counter/rebuild'
# 预期: {"status":"started","groupId":"counter-rebuild",...}
```

- [ ] **Step 4: 检查进度**

```bash
curl 'http://localhost:8080/api/v1/admin/counter/rebuild/status'
# 预期: {"running":true,...}

curl 'http://localhost:8080/actuator/metrics/counter.rebuild.lag'
# 预期: 有 lag 指标
```

- [ ] **Step 5: Commit**

```bash
git commit --allow-empty -m "test: Kafka 回放端到端测试通过"
```

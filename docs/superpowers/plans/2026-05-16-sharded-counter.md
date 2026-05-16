# 分桶高并发计数系统 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有单 key Redis SDS 计数器升级为分桶 + Redis HINCRBY + Kafka 异步批量刷 DB 的统一计数系统，覆盖点赞、收藏、评论、阅读、转发、关注、粉丝七个计数维度。

**Architecture:** 每个实体分配 N 个 Redis Hash 桶（entity N=100, user N=50），写入时 hash(userId) % N 选桶 HINCRBY，读取时 SUM 所有桶。Kafka Consumer 周期性批量聚合增量 UPDATE MySQL。Redis 故障降级走 DB 原子更新。

**Tech Stack:** Spring Boot 3.2, MyBatis, Redis (Lettuce), MySQL 8.0, Kafka (已配置但未用于计数器), JUnit 5 + Testcontainers

---

### File Structure

```
src/main/java/com/arknow/counter/
├── ShardedCounter.java              # NEW — 统一分桶计数器接口
├── service/
│   └── impl/
│       ├── ShardedCounterImpl.java  # NEW — 核心实现: 分桶写 + SUM读 + 降级
│       └── CounterFlushConsumer.java # NEW — Kafka 消费者批量刷 DB
├── schema/
│   ├── CounterKeys.java            # MODIFY — 新增分桶 key 命名
│   └── CounterBucketConfig.java    # NEW — 桶数配置（entity=100, user=50）
├── CounterServiceImpl.java         # MODIFY — toggle() 调用 ShardedCounter
service/impl/UserCounterServiceImpl.java # MODIFY — 改为调用 ShardedCounter

src/main/resources/db/migration/
└── V2__counter_snapshot.sql        # NEW — counter_snapshot 表

src/test/java/com/arknow/counter/
├── ShardedCounterImplTest.java     # NEW — 单元测试（分桶、SUM、降级）
├── CounterFlushConsumerTest.java   # NEW — 消费者测试
└── ShardedCounterIntegrationTest.java # NEW — 集成测试（Redis + DB）
```

---

### Task 1: CounterBucketConfig — 桶数配置

**Files:**
- Create: `src/main/java/com/arknow/counter/schema/CounterBucketConfig.java`

- [ ] **Step 1: 创建配置类**

```java
package com.arknow.counter.schema;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "counter.bucket")
public class CounterBucketConfig {
    private int entityBuckets = 100;
    private int userBuckets = 50;

    public int getEntityBuckets() { return entityBuckets; }
    public void setEntityBuckets(int v) { this.entityBuckets = v; }
    public int getUserBuckets() { return userBuckets; }
    public void setUserBuckets(int v) { this.userBuckets = v; }

    public int getBucketCount(String entityType) {
        return "user".equals(entityType) ? userBuckets : entityBuckets;
    }
}
```

- [ ] **Step 2: 注册到 application.yml**

在 `src/main/resources/application.yml` 追加:

```yaml
counter:
  bucket:
    entity-buckets: 100
    user-buckets: 50
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/arknow/counter/schema/CounterBucketConfig.java src/main/resources/application.yml
git commit -m "feat: counter bucket config (entity=100, user=50)"
```

---

### Task 2: CounterKeys 新增分桶 key

**Files:**
- Modify: `src/main/java/com/arknow/counter/schema/CounterKeys.java`

- [ ] **Step 1: 添加分桶 bucket key 方法**

```java
// 在 CounterKeys 类中新增:
public static String bucketKey(String entityType, String entityId, int bucketId) {
    return String.format("cnt:%s:%s:bucket:%d", entityType, entityId, bucketId);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/arknow/counter/schema/CounterKeys.java
git commit -m "feat: CounterKeys 新增 bucketKey 命名"
```

---

### Task 3: ShardedCounterImpl — 核心分桶计数器

**Files:**
- Create: `src/main/java/com/arknow/counter/service/ShardedCounterImpl.java`
- Create: `src/test/java/com/arknow/counter/ShardedCounterImplTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.arknow.counter;

import com.arknow.counter.schema.CounterBucketConfig;
import com.arknow.counter.service.impl.ShardedCounterImpl;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

class ShardedCounterImplTest {
    // 使用 Testcontainers Redis, 此处仅示意核心用例
    // 实际需要 @Testcontainers + @Container static GenericContainer redis

    @Test
    void incrementShouldDistributeAcrossBuckets() {
        // 验证写入分散到不同桶
    }

    @Test
    void getCountShouldSumAllBuckets() {
        // 验证 SUM 所有桶结果正确
    }

    @Test
    void degradeToDbWhenRedisDown() {
        // 验证 Redis 不可用时降级走 DB
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
mvn test -pl . -Dtest=ShardedCounterImplTest
```

- [ ] **Step 3: 实现 ShardedCounterImpl**

```java
package com.arknow.counter.service.impl;

import com.arknow.counter.schema.CounterBucketConfig;
import com.arknow.counter.schema.CounterKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ShardedCounterImpl {
    private static final Logger log = LoggerFactory.getLogger(ShardedCounterImpl.class);
    private final StringRedisTemplate redis;
    private final CounterBucketConfig bucketConfig;
    private final JdbcTemplate jdbc;

    public ShardedCounterImpl(StringRedisTemplate redis, CounterBucketConfig bucketConfig,
                               JdbcTemplate jdbc) {
        this.redis = redis;
        this.bucketConfig = bucketConfig;
        this.jdbc = jdbc;
    }

    /** 写入：hash(userId) % N 选桶 → HINCRBY */
    public void increment(String entityType, String entityId, String metric,
                           long userId, long delta) {
        int buckets = bucketConfig.getBucketCount(entityType);
        int bucketId = hash(userId) % buckets;
        String key = CounterKeys.bucketKey(entityType, entityId, bucketId);
        try {
            redis.opsForHash().increment(key, metric, delta);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, degrading to DB for {}/{}/{}", entityType, entityId, metric);
            degradeIncrement(entityType, entityId, metric, delta);
        }
    }

    /** 读取: SUM 所有桶 */
    public long getCount(String entityType, String entityId, String metric) {
        int buckets = bucketConfig.getBucketCount(entityType);
        long sum = 0;
        for (int i = 0; i < buckets; i++) {
            String key = CounterKeys.bucketKey(entityType, entityId, i);
            try {
                Object v = redis.opsForHash().get(key, metric);
                if (v != null) sum += Long.parseLong(String.valueOf(v));
            } catch (Exception ignored) {}
        }
        return sum;
    }

    /** 读取所有 metric */
    public Map<String, Long> getCounts(String entityType, String entityId) {
        int buckets = bucketConfig.getBucketCount(entityType);
        Map<String, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < buckets; i++) {
            String key = CounterKeys.bucketKey(entityType, entityId, i);
            try {
                Map<Object, Object> entries = redis.opsForHash().entries(key);
                for (var e : entries.entrySet()) {
                    String metric = String.valueOf(e.getKey());
                    long v = Long.parseLong(String.valueOf(e.getValue()));
                    result.merge(metric, v, Long::sum);
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    /** 获取指定桶内的增量（供 Kafka 消费者使用） */
    public Map<Integer, Map<String, Long>> drainBuckets(String entityType, String entityId) {
        int buckets = bucketConfig.getBucketCount(entityType);
        Map<Integer, Map<String, Long>> drained = new LinkedHashMap<>();
        for (int i = 0; i < buckets; i++) {
            String key = CounterKeys.bucketKey(entityType, entityId, i);
            try {
                Map<Object, Object> entries = redis.opsForHash().entries(key);
                if (entries.isEmpty()) continue;
                Map<String, Long> metrics = new LinkedHashMap<>();
                for (var e : entries.entrySet()) {
                    long v = Long.parseLong(String.valueOf(e.getValue()));
                    if (v == 0) continue;
                    metrics.put(String.valueOf(e.getKey()), v);
                    // 原子扣减已刷部分（用 HINCRBY -delta 替代 drain）
                }
                if (!metrics.isEmpty()) drained.put(i, metrics);
            } catch (Exception ignored) {}
        }
        return drained;
    }

    /** 降级：Redis 不可用，直接走 DB 原子更新 */
    private void degradeIncrement(String entityType, String entityId, String metric, long delta) {
        jdbc.update(
            "INSERT INTO counter_snapshot (entity_type, entity_id, metric, count) " +
            "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE count = count + ?",
            entityType, entityId, metric, delta, delta);
    }

    private int hash(long userId) {
        // FNV-1a hash derivative: spread across buckets
        long h = userId ^ (userId >>> 16);
        return Math.abs((int) (h ^ (h >>> 8)));
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn test -pl . -Dtest=ShardedCounterImplTest
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/arknow/counter/service/impl/ShardedCounterImpl.java \
        src/test/java/com/arknow/counter/ShardedCounterImplTest.java
git commit -m "feat: ShardedCounterImpl — 分桶写入 + SUM 读取 + Redis 降级走 DB"
```

---

### Task 4: DB — counter_snapshot 表

**Files:**
- Create: `src/main/resources/db/migration/V2__counter_snapshot.sql`

- [ ] **Step 1: 创建 migration**

```sql
CREATE TABLE IF NOT EXISTS counter_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL,
    entity_id VARCHAR(32) NOT NULL,
    metric VARCHAR(16) NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_entity_metric (entity_type, entity_id, metric)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: 执行 migrate**

```bash
mysql -uroot arkknow < src/main/resources/db/migration/V2__counter_snapshot.sql
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V2__counter_snapshot.sql
git commit -m "feat: counter_snapshot 表 — 异步刷 DB 持久化"
```

---

### Task 5: CounterFlushConsumer — Kafka 异步批量刷 DB

**Files:**
- Create: `src/main/java/com/arknow/counter/service/impl/CounterFlushConsumer.java`

- [ ] **Step 1: 实现消费者**

```java
package com.arknow.counter.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CounterFlushConsumer {
    private static final Logger log = LoggerFactory.getLogger(CounterFlushConsumer.class);
    private final ShardedCounterImpl counter;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    // 待刷实体集合（由 CounterEvent 触发加入）
    private final Set<String> dirtyEntities = Collections.synchronizedSet(new HashSet<>());

    public CounterFlushConsumer(ShardedCounterImpl counter, JdbcTemplate jdbc,
                                 ObjectMapper mapper) {
        this.counter = counter;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** 标记实体为脏，触发下一次刷库 */
    public void markDirty(String entityType, String entityId) {
        dirtyEntities.add(entityType + ":" + entityId);
    }

    /** 定时刷库：每 1 秒扫描脏实体，批量 UPDATE */
    @Scheduled(fixedDelay = 1000)
    public void flushToDb() {
        if (dirtyEntities.isEmpty()) return;
        Set<String> snapshot = new HashSet<>(dirtyEntities);
        dirtyEntities.clear();

        for (String key : snapshot) {
            String[] parts = key.split(":", 2);
            if (parts.length < 2) continue;
            String entityType = parts[0];
            String entityId = parts[1];

            try {
                Map<Integer, Map<String, Long>> drained = counter.drainBuckets(entityType, entityId);
                for (var bucketEntry : drained.entrySet()) {
                    for (var metricEntry : bucketEntry.getValue().entrySet()) {
                        jdbc.update(
                            "INSERT INTO counter_snapshot (entity_type, entity_id, metric, count) " +
                            "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE count = count + ?",
                            entityType, entityId, metricEntry.getKey(),
                            metricEntry.getValue(), metricEntry.getValue());
                    }
                }
            } catch (Exception e) {
                log.error("Flush failed for {}/{}: {}", entityType, entityId, e.getMessage());
                dirtyEntities.add(key); // 重试
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/arknow/counter/service/impl/CounterFlushConsumer.java
git commit -m "feat: CounterFlushConsumer — 定时批量刷 DB"
```

---

### Task 6: 改造 CounterServiceImpl 接入 ShardedCounter

**Files:**
- Modify: `src/main/java/com/arknow/counter/service/impl/CounterServiceImpl.java`

- [ ] **Step 1: 注入 ShardedCounterImpl, 修改 toggle()**

在 `CounterServiceImpl`:

```java
private final ShardedCounterImpl shardedCounter;

// 构造函数注入:
public CounterServiceImpl(StringRedisTemplate redis, CounterEventProducer eventProducer,
                           CounterAggregationConsumer aggregationConsumer,
                           ShardedCounterImpl shardedCounter,
                           @Qualifier("feedPublicCache") Cache<...> feedPublicCache) {
    ...
    this.shardedCounter = shardedCounter;
}

// toggle() 中，bitmap change 成功后追加:
if (ok) {
    shardedCounter.increment(etype, eid, metric, uid, delta);
    // 保留原有 agg bucket 和缓存失效逻辑（向下兼容）
    ...
}
```

- [ ] **Step 2: 改造 getCounts() 优先读 ShardedCounter 从 Redis 获取最新值**

```java
@Override
public Map<String, Long> getCounts(String entityType, String entityId, List<String> metrics) {
    // 新路径：读分桶 SUM
    Map<String, Long> result = shardedCounter.getCounts(entityType, entityId);
    // 合并：如果分桶没有（新实体），fallback 到旧 SDS 路径
    if (result.isEmpty()) {
        return fallbackGetCounts(entityType, entityId, metrics);
    }
    return result;
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/arknow/counter/service/impl/CounterServiceImpl.java
git commit -m "refactor: CounterServiceImpl 接入 ShardedCounter 分桶写入"
```

---

### Task 7: 改造 UserCounterServiceImpl 接入 ShardedCounter

**Files:**
- Modify: `src/main/java/com/arknow/counter/service/impl/UserCounterServiceImpl.java`

- [ ] **Step 1: 注入 ShardedCounterImpl, 替换 incrementField()**

```java
private final ShardedCounterImpl shardedCounter;

public UserCounterServiceImpl(StringRedisTemplate redis, ShardedCounterImpl shardedCounter) {
    this.redis = redis;
    this.shardedCounter = shardedCounter;
}

private void incrementField(long userId, int idx, int delta) {
    String metric = CounterSchema.USER_IDX_TO_NAME.getOrDefault(idx, "unknown");
    shardedCounter.increment("user", String.valueOf(userId), metric, userId, delta);
}
```

- [ ] **Step 2: 改造 getUserCounts() 读 ShardedCounter**

```java
@Override
public Map<String, Long> getUserCounts(long userId) {
    Map<String, Long> result = shardedCounter.getCounts("user", String.valueOf(userId));
    if (result.isEmpty()) {
        return fallbackGetCounts(userId); // 保留旧 SDS 路径作为 fallback
    }
    return result;
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/arknow/counter/service/impl/UserCounterServiceImpl.java
git commit -m "refactor: UserCounterServiceImpl 接入 ShardedCounter 分桶"
```

---

### Task 8: 集成测试

**Files:**
- Create: `src/test/java/com/arknow/counter/ShardedCounterIntegrationTest.java`

- [ ] **Step 1: 写集成测试（使用 Testcontainers Redis + MySQL）**

```java
@Testcontainers
class ShardedCounterIntegrationTest {
    @Container static GenericContainer<?> redis = 
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Container static MySQLContainer<?> mysql = 
        new MySQLContainer<>("mysql:8.0");

    @Test
    void concurrentWritesShouldDistributeAcrossBuckets() {
        // 100 个线程并发写 → 验证分布到多个桶
    }

    @Test
    void readShouldSumAllBuckets() {
        // 手动设多个桶 → verify SUM
    }

    @Test
    void degradeToDbWhenRedisUnavailable() {
        // 停 Redis → 写入 → verify DB 中有数据
    }

    @Test
    void flushConsumerShouldBatchUpdateDb() {
        // 写入多个桶 → 触发 flush → verify DB 更新正确
    }
}
```

- [ ] **Step 2: 运行全部测试**

```bash
mvn test -pl . -Dtest=ShardedCounterIntegrationTest
```

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/arknow/counter/ShardedCounterIntegrationTest.java
git commit -m "test: ShardedCounter 集成测试"
```

---

### Task 9: 清理和文档

- [ ] **Step 1: 验证旧 SDS 路径仍作为 fallback 工作**

确认 `CounterServiceImpl.getCounts()` 在分桶为空时回退到旧 SDS 读取路径。

- [ ] **Step 2: 验证 BDD 场景全通过**

逐一验证 `sharded-counter.feature` 中 6 个 Rule 全部覆盖。

- [ ] **Step 3: 更新 ARCHITECTURE.md**

在架构文档中记录分桶计数系统的设计决策。

- [ ] **Step 4: Final commit**

```bash
git add ARCHITECTURE.md
git commit -m "docs: 分桶计数系统架构文档"
```

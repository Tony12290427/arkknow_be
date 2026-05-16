# CounterFlushConsumer — Mini Implementation Plan

**Goal:** 实现定时批量刷 DB 组件，将 Redis 分桶中的增量周期性地原子 drain 并批量 UPDATE MySQL。

**Architecture:** `@Scheduled(fixedDelay=1000)` 每秒扫描脏实体集合，逐个 drain 所有桶，批量写入 `counter_snapshot` 表。

**Files:**
- Create: `src/main/java/com/arknow/counter/service/impl/CounterFlushConsumer.java`

**Steps:**

### Task 1: CounterFlushConsumer

- [ ] 创建 CounterFlushConsumer
- [ ] 注入 ShardedCounterImpl + JdbcTemplate
- [ ] `markDirty(entityType, entityId)` 标记待刷实体
- [ ] `@Scheduled flushToDb()` 定时扫描 + drain + batch UPDATE
- [ ] 在 CounterServiceImpl.toggle() 中调用 `markDirty()`
- [ ] 编译 + 重启 + 验证

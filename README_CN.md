# 知舟 (ArkKnow) — 知识获取与分享社区

> 书山有路勤为径，学海无涯苦作舟

基于 Java 21 + Spring Boot 3.2.4 构建的知识获取与分享社区平台。

🔗 **前端仓库**: [zhizhou_react](https://github.com/Tony12290427/zhizhou_react)

📐 **架构设计文档**: 参见 [ARCHITECTURE.md](./ARCHITECTURE.md) — 完整系统架构、AI 搜索流水线、高并发优化、性能数据。

## 技术栈

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 运行时 | Java | 21 | — |
| 框架 | Spring Boot | 3.2.4 | 应用框架 |
| ORM | MyBatis | 3.0.3 | SQL 映射 |
| 数据库 | MySQL | 8.x | 主数据存储 |
| L1 缓存 | Caffeine | 3.1.8 | 进程内缓存（1000条/10min TTL） |
| L2 缓存 | Redis（Lettuce + Redisson） | 7.x + 3.52.0 | 分布式缓存 + 分布式锁 + 计数器 |
| 搜索引擎 | Elasticsearch | 9.2.1 | BM25 全文搜索 + kNN 向量检索 |
| 消息队列 | Apache Kafka（Spring Kafka） | 3.x | Outbox 事件 → ES 索引同步 |
| CDC | Alibaba Canal | 1.1.8 | MySQL binlog → Outbox → Kafka |
| AI 模型 | DeepSeek v4-pro（Spring AI） | 1.0.3 | AI 搜索回答生成 |
| 嵌入模型 | OpenAI text-embedding-3-small | — | 文本 → 1536 维向量 |
| LLM 保护 | Resilience4j | 2.2.0 | 熔断 + 重试x3 + 15s 超时 |
| SSE | Reactor 3.6 | — | LLM 流式输出 |
| Markdown | flexmark-java | 0.64.8 | Markdown → HTML（GFM 表格 + 排版） |
| 认证 | Spring Security 6 + JWT（RSA 密钥对） | — | 15min 访问令牌 + 7天 刷新令牌 |
| 对象存储 | 阿里云 OSS | 3.17.3 | 图片/视频上传 |
| 邮件 | Spring Boot Mail | — | 邮箱验证码 |
| 监控 | Micrometer + Spring Actuator | — | 指标暴露 |
| 配置 | spring-dotenv | 4.0.0 | .env 文件加载 |
| 测试 | JUnit 5 + Mockito | — | 单元测试 + 集成测试 |

## 功能模块

### 核心平台
- JWT 双令牌认证（RS256 RSA 密钥对 + Redis 刷新令牌白名单）
- 渐进式内容发布（OSS 预签名直传）
- 知识帖文 CRUD + 可见性控制 + 置顶
- 评论系统（含软删除 + @提及支持）
- 通知系统（关注、点赞、评论、收藏事件）
- 收藏集系统（CRUD + 条目管理）
- 用户资料管理（局部更新、头像上传）
- 标签提取与分类管理

### 社交关系
- 关注/取消关注 + 三态查询（following / followedBy / mutual）
- 关注/粉丝列表分页
- Outbox + Canal + Kafka 事件驱动投影（One Master, Multiple Slaves）
- 令牌桶限流（Redis Lua，容量 100，速率 1/s）

### 高并发计数系统
- **Redis SDS 紧凑二进制计数**：单实体/用户 5 个 int32 指标仅占一个 20 字节 key
- **分桶计数器 + 异步刷 DB**：Hash 分桶消除热点键竞争
- **分片位图幂等**：32K bit/shard，O(1) 单用户判重，零竞争 toggle
- **降级路径**：Redis 故障 → 直接 `UPDATE SET count = count + delta` 走 DB
- **Kafka 写聚合**：细粒度写入 → 批量刷 DB（可配置间隔）
- 覆盖：点赞、收藏、评论、阅读、转发（实体）+ 关注、粉丝、帖子、获赞、收藏（用户）

### 搜索
- Elasticsearch BM25 全文搜索 + smartcn 中文分词
- `function_score` BM25 文本相关性 + 点赞数业务权重
- ngram 子串匹配（1-3 字符）模糊搜索
- 多策略查询：`match_phrase`（精准）+ `multi_match`（广召回）+ MostFields
- 发布即索引：新文章自动同步 ES

### AI 混合搜索
- **混合搜索**：BM25 关键词 + OpenAI Embedding 向量 → RRF 倒数秩融合（k=60）→ 排序
- **DeepSeek LLM 流式回答**：结构化回答（标题、表格、列表）
- **SSE 流式输出**：Reactor Flux → SSE 实时推送，前端流式渲染 Markdown
- **Markdown→HTML 服务端渲染**：flexmark-java GFM 表格 + 排版，XSS 安全（ESCAPE_HTML）
- **两级缓存**：Caffeine L1（1000条/10min）+ Redis L2（1h TTL），命中率 >60%
- **并行查询**：`CompletableFuture` 同时执行 ES + 向量查询
- **熔断保护**：Resilience4j（50% 失败 → 断路 30s）+ 重试x3 + 15s 超时
- **令牌桶限流**：Redis Lua 脚本，10次/分钟/IP

### RAG 知识问答
- 两级提示词策略：文章上下文 → 模型自身知识兜底
- 懒索引：首次提问触发切段+嵌入
- 切段策略：按二级标题切分，最长 1200 字符，200 字符重叠，短段合并
- 向量存储：Elasticsearch，相似度阈值 0.72
- 禁用机械话术："上下文为空""内容未提及"等
- AI 摘要：DeepSeek 生成 ≤50 字中文描述

### Feed 缓存
- 三级缓存：Caffeine L2 + Redis L1（页面骨架）+ Redis L0（片段）
- 单飞锁合并：并发 cache miss 合并为一次 DB 查询
- 热键延长：滑动窗口检测，热点页 TTL 自动延长
- 精准失效：只清除受影响的页面缓存
- SDS 批量读取：Redis pipeline 一次往返拿整页计数

### 管理后台（17 个 Controller）
- 认证（登录、刷新、登出、我的信息）
- 用户管理（列表、详情、删除、批量删除）
- 帖子管理 + 审核（通过/拒绝）
- 评论管理
- 标签/分类管理
- 收藏集管理
- 关注关系管理
- 通知管理
- 系统监控 + 统计
- Redis 会话查看
- 管理员角色管理（提权/降权）
- 点赞数据查看（Redis）
- API 文档浏览器

### 安全设计
- JWT RSA 密钥对（access 15min 无状态 + refresh 7d Redis 白名单）
- 令牌轮换（刷新时撤销旧令牌，检测 token 被盗）
- 私钥 `private.pem` gitignored
- API key 通过 `.env` 加载（gitignored），spring-dotenv 解析
- CORS 配置
- XSS 防护：flexmark `ESCAPE_HTML` + `SUPPRESS_HTML`
- CSRF tokens（突变端点）
- 令牌桶限流（按 IP 和按用户）
- Spring Security 6 + BCrypt 密码编码

## API 接口

### 认证 (`/api/v1/auth`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/send-code` | 否 | 发送验证码（手机号/邮箱） |
| POST | `/register` | 否 | 验证码注册 |
| POST | `/login` | 否 | 密码或验证码登录 |
| POST | `/token/refresh` | 否 | 刷新访问令牌（令牌轮换） |
| POST | `/logout` | 是 | 登出并撤销刷新令牌 |
| POST | `/password/reset` | 否 | 验证码重置密码 |
| GET | `/me` | 是 | 查询当前用户信息 |

### 用户资料 (`/api/v1/profile`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/{userId}` | 否 | 获取用户资料 |
| PATCH | `/` | 是 | 部分更新资料字段 |
| POST | `/avatar` | 是 | 上传头像（multipart） |

### 知识帖文 (`/api/v1/knowposts`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/drafts` | 是 | 创建草稿（雪花算法 ID） |
| POST | `/{id}/content/confirm` | 是 | 确认 OSS 上传（ETag/SHA256 校验） |
| PATCH | `/{id}` | 是 | 更新元数据（标题、标签等） |
| POST | `/{id}/publish` | 是 | 发布草稿 |
| PATCH | `/{id}/top` | 是 | 置顶/取消置顶 |
| PATCH | `/{id}/visibility` | 是 | 修改可见性 |
| DELETE | `/{id}` | 是 | 软删除 |
| GET | `/feed` | 否 | 公开 Feed（分页） |
| GET | `/detail/{id}` | 否 | 帖文详情 |
| GET | `/mine` | 是 | 我的帖文 |
| GET | `/user/{userId}` | 否 | 指定用户的帖文 |

### 评论 (`/api/v1/comments`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/post/{postId}` | 否 | 帖文评论列表 |
| POST | `/` | 是 | 创建评论 |
| DELETE | `/{id}` | 是 | 软删除评论 |

### 点赞/收藏 (`/api/v1/action`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/like` | 是 | 点赞（幂等，重复点赞无副作用） |
| POST | `/unlike` | 是 | 取消点赞 |
| POST | `/fav` | 是 | 收藏（幂等） |
| POST | `/unfav` | 是 | 取消收藏 |

### 计数查询 (`/api/v1/counter`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/{etype}/{eid}` | 否 | 查询实体计数（like, fav 等） |

### 用户关系 (`/api/v1/relation`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/follow` | 是 | 关注用户（限流保护） |
| POST | `/unfollow` | 是 | 取消关注 |
| GET | `/status` | 是 | 三态关系查询（following/followedBy/mutual） |
| GET | `/following/{userId}` | 否 | 关注列表（分页） |
| GET | `/followers/{userId}` | 否 | 粉丝列表（分页） |

### 通知 (`/api/v1/notifications`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/` | 是 | 通知列表 |
| PATCH | `/read` | 是 | 全部标为已读 |

### 收藏集 (`/api/v1/collections`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/` | 是 | 创建收藏集 |
| GET | `/` | 是 | 我的收藏集列表 |
| GET | `/{id}` | 否 | 收藏集详情 |
| PATCH | `/{id}` | 是 | 更新收藏集 |
| DELETE | `/{id}` | 是 | 删除收藏集 |
| POST | `/{id}/items` | 是 | 添加条目 |
| DELETE | `/{id}/items/{itemId}` | 是 | 移除条目 |

### 对象存储 (`/api/v1/storage`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/presign` | 是 | 获取 OSS 预签名上传 URL。支持类型白名单（image/jpeg, image/png, image/gif, image/webp, video/mp4, video/webm, video/mov）和文件大小限制（图片10MB, 视频100MB） |

### 搜索 (`/api/v1/search`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/ai?q=&topK=` | 否 | **混合 AI 搜索**：BM25+向量 RRF → DeepSeek SSE 流 |
| GET | `/suggest?prefix=` | 否 | 前缀补全建议 |
| GET | `/?keyword=` | 否 | 传统全文搜索 |

### 标签 & 分类 (`/api/v1`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/tags` | 否 | 全部标签 |
| GET | `/categories` | 否 | 全部分类 |

### 管理后台 (`/api/v1/admin`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/auth/login` | 否 | 管理员登录 |
| POST | `/auth/refresh` | 否 | 刷新令牌 |
| GET | `/auth/me` | 是 | 管理员信息 |
| GET | `/users` | 是 | 用户列表 |
| GET | `/users/{id}` | 是 | 用户详情 |
| DELETE | `/users/{id}` | 是 | 删除用户 |
| POST | `/users/batch-delete` | 是 | 批量删除 |
| GET | `/posts` | 是 | 全部帖文 |
| PATCH | `/posts/{id}/audit` | 是 | 审核帖文 |
| GET | `/comments` | 是 | 全部评论 |
| DELETE | `/comments/{id}` | 是 | 删除评论 |
| GET | `/tags` | 是 | 标签管理 |
| GET | `/categories` | 是 | 分类管理 |
| GET | `/audit-logs` | 是 | 登录审计日志 |
| GET | `/collections` | 是 | 全部收藏集 |
| GET | `/follows` | 是 | 全部关注关系 |
| POST | `/notifications` | 是 | 发送系统通知 |
| GET | `/sessions` | 是 | Redis 会话查看 |
| POST | `/admins/{id}/promote` | 是 | 提权为管理员 |
| POST | `/admins/{id}/demote` | 是 | 降权 |
| GET | `/monitor` | 是 | 系统统计 |
| GET | `/likes` | 是 | 点赞数据查看（Redis） |
| GET | `/api-docs` | 是 | API 文档 |

## 快速开始

```bash
# 前置条件：Java 21, Maven, MySQL 8, Redis 7, Docker

# 启动基础设施
brew services start redis
docker run -d --name elasticsearch -p 9200:9200 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
  --memory 1g \
  docker.elastic.co/elasticsearch/elasticsearch:9.0.0

# 创建数据库
mysql -u root -e "CREATE DATABASE IF NOT EXISTS arkknow DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root arkknow < db/schema.sql

# 运行应用
mvn spring-boot:run
```

安装 ES 中文分词插件：
```bash
docker exec elasticsearch bin/elasticsearch-plugin install analysis-smartcn
docker restart elasticsearch
```

Elasticsearch 是可选的 — 没有 ES 也能启动，搜索功能降级返回空结果。

## 运行测试

```bash
bash test_all.sh
```

## API 约定

- 基础路径：`/api`
- 版本前缀：`/api/v1`
- 鉴权方式：`Authorization: Bearer <access_token>`
- 分页参数：`?limit=&offset=`（最大 limit 100）

## 项目结构

```
com.arknow/
├── admin/api/                  # 管理后台（17 个 Controller）
├── auth/                       # 认证（JWT、验证码、登录注册）
│   ├── api/                    # Controller + DTO
│   ├── audit/                  # 登录审计日志
│   ├── config/                 # 安全配置
│   ├── model/                  # 领域模型
│   ├── service/                # AuthService
│   ├── token/                  # JwtService、RefreshTokenStore
│   ├── util/                   # 工具
│   └── verification/           # 验证码生成/存储/发送
├── profile/                    # 用户资料管理
├── knowpost/                   # 知识帖文系统
│   ├── api/                    # KnowPostController + DTO
│   ├── id/                     # SnowflakeIdGenerator（雪花算法）
│   ├── listener/               # FeedCacheInvalidationListener
│   ├── mapper/                 # KnowPostMapper
│   ├── model/                  # KnowPost, FeedRow, DetailRow
│   └── service/                # KnowPostService, FeedService
├── counter/                    # 🌟 高并发计数系统（核心模块）
│   ├── api/                    # ActionController, CounterController
│   ├── config/                 # ShardedCounterConfig
│   ├── event/                  # FlushEvent, CounterEvent
│   ├── schema/                 # CounterSchema, CounterKeys, BitmapShard
│   └── service/
│       ├── ShardedCounter      # 统一分桶计数器接口
│       ├── CounterService      # 实体计数器（like/fav toggle）
│       ├── UserCounterService  # 用户维度计数器
│       └── impl/
│           ├── ShardedCounterImpl   # hash % N → Redis Hash INCR
│           ├── CounterServiceImpl   # Toggle + bitmap + SDS
│           └── UserCounterServiceImpl # Lua SDS incr
├── relation/                   # 🌟 用户关系（关注/取关）
│   ├── api/                    # RelationController
│   ├── event/                  # RelationEvent
│   ├── mapper/                 # RelationMapper
│   ├── outbox/                 # OutboxMapper, CanalOutboxConsumer
│   ├── processor/              # RelationEventProcessor
│   └── service/                # RelationService（One Master, Multiple Slaves）
├── comment/                    # 评论系统（软删除）
├── collection/                 # 收藏集管理
├── notification/               # 通知系统
├── storage/                    # 对象存储（OSS）
├── search/                     # ES 搜索 + AI 搜索
│   ├── api/                    # SearchController, AiSearchController
│   ├── cache/                  # AiSearchCache（Caffeine L1 + Redis L2）
│   ├── index/                  # SearchIndexService
│   ├── outbox/                 # ES Outbox 同步
│   ├── ratelimit/              # 令牌桶限流
│   └── service/                # SearchService, AiSearchService
├── llm/                        # AI/LLM 集成
│   ├── rag/                    # RagIndexService, RagQueryService
│   └── service/                # DeepSeek 集成 + SSE 流式
├── tag/                        # 标签管理
├── cache/                      # 缓存基础设施
│   ├── config/                 # CacheConfig, CacheProperties
│   └── hotkey/                 # HotKeyDetector（滑动窗口）
├── common/                     # 全局异常处理、错误码
├── config/                     # SecurityConfig, WebConfig, ES 配置
└── user/                       # 用户领域、Mapper、Service
```

## 核心架构设计

### AI 搜索流水线

```
前端 SSE 请求 → 令牌桶限流（10次/min/IP）
  → AiSearchCache（Caffeine L1 → Redis L2, SHA-256 key）
  → 缓存未命中:
    → ES BM25（function_score + phrase match 10x + ngram recall）
    → 向量 kNN（OpenAI Embedding → 余弦相似度, 阈值 0.68）
    → 并行执行（CompletableFuture）
  → RRF 融合（k=60）: RRF_score = 1/(60+rank_es) + 1/(60+rank_vector)
  → 取 top-K 文章
  → DeepSeek v4-pro 生成回答
  → SSE 流式输出（raw text → [HTML] → [ARTICLES] → [DONE]）
  → 写入 L1 + L2 缓存
```

性能特征：

| 场景 | 延迟 | 瓶颈 |
|------|------|------|
| 缓存命中 L1 | <1ms | — |
| 缓存命中 L2 | <5ms | Redis 网络 |
| 缓存未命中，ES + 向量 | ~50ms | ES 查询 |
| 缓存未命中，完整管线 | 2-10s | DeepSeek LLM API |

### 关注关系 — One Master, Multiple Slaves + Outbox

```
follow(fromUserId, toUserId):
  1. Redis 令牌桶限流: rl:follow:{fromUserId}（容量 100, 速率 1/s）
  2. 幂等检查: existsFollowing(fromUserId, toUserId)
  3. 单事务写:
     a. INSERT following (relationId, fromUserId, toUserId, status=1)
     b. INSERT outbox (eventType="FollowCreated", payload=JSON)
  4. 同步更新投影:
     - 计数器: following +1, followers +1
     - 通知: INSERT notification (type="follow")
  5. 异步管道（Outbox → Canal → Kafka）:
     - Follower 表投影
     - ES 索引同步
     - 计数器刷 DB（计划中）
```

**设计原则**：`following` 表是唯一真相源。`follower` 表、计数器、缓存列表都是从 Outbox 事件异步投影的。这消除了经典的"双写不一致"问题。

### 高并发计数系统

**SDS 二进制结构**（`CounterSchema`）：
单个 Redis key 存储 5 个 int32（big-endian，共 20 字节）：
```
ucnt:{userId} = [followings(4B)][followers(4B)][posts(4B)][likedPosts(4B)][favedPosts(4B)]
```

**分桶计数器**（`ShardedCounter`）：
```
写: cnt:{entityType}:{entityId}:bucket:{hash(userId) % N}
  → HINCRBY metric delta（Redis Hash 原子增量）

读: SUM(HGETALL 所有 N 个桶) → 返回总量
```

桶数：`N = ceil(目标写QPS / 单key安全QPS) × 5`
- Entity 计数器：N = 100
- User 计数器：N = 50

**异步刷 DB**（`CounterFlushConsumer`，计划中）：
```
Kafka Consumer:
  poll(1000ms, max 500 events)
    → group by (entityType, entityId, metric)
    → DB: UPDATE counter SET count = count + sum(deltas) WHERE ...
    → Redis: 从桶中扣减已刷增量（Lua 原子操作）
```

**降级链路**：

| 故障 | 策略 |
|------|------|
| Redis 挂 | 降级走 DB `UPDATE SET count = count + delta` |
| Kafka 积压 | 队列深度 > 10000 暂停入队，同步走 Redis |
| DB 刷库失败 | Redis 数据保留，Consumer 重试 + 告警 |
| Redis + DB 全挂 | 熔断，返回缓存最后值 |
| 全部恢复后 | 从位图事实层重建所有计数器 |

### 缓存策略

- **AI 搜索**：Caffeine L1（SHA-256 key, 10min TTL）+ Redis L2（1h TTL），目标 >60% 命中率
- **Feed/列表**：Redis 缓存页面，发布/点赞/收藏时精准失效
- **计数器**：纯 Redis，读即最新值，异步刷 DB

## 已知注意事项

- **Snowflake ID 在 JS 中**：`Number()` 截断 >2^53 的大数，前端必须用字符串
- **`Math.abs(Integer.MIN_VALUE)`**：Java 中返回负数 — 分桶 hash 用 `& Integer.MAX_VALUE`
- **关注幂等**：`existsFollowing` 检查和 `insertFollowing` 之间存在竞态窗口 — try-catch 兜底
- **计数器更新与主流程解耦**：try-catch 吞异常，计数器失败不阻断关注主流程

## 安全与 Commit 规范

**绝对红线 — 禁止提交：**

| 类别 | 示例 |
|------|------|
| 云服务 AccessKey | `LTAI...`、AccessKey Secret |
| API 密钥 | `sk-proj-...`、`sk-...` |
| 数据库密码 | 真实密码值 |
| JWT 私钥 | `private.pem`、RSA 私钥 |
| 服务器 IP | 公网地址（文档中用 `<server-ip>`） |
| SSH 密钥 | `.pem` 私钥文件 |

**Commit message 禁用措辞**：`remove key`、`fix leak`、`清理密钥`、`修复泄露`

**密钥已泄露的修复流程**：`git filter-branch` 清除全部历史 → 删除备份 refs → `git gc` → force push → 去对应平台吊销旧密钥换新
- **Outbox 同步处理**：MVP 阶段在 `writeOutboxEvent` 内同步调用 processor，生产环境应移除改走 Kafka
- **CounterFlushConsumer 未实现**：分桶方案异步刷 DB 消费者在计划中（Task 5），目前计数器仅存 Redis 无 DB 持久化
- **无 CI/CD**：目前 Docker Compose 本地运行，无生产部署
- **Elasticsearch 可选**：无 ES 也能启动，搜索降级返回空结果

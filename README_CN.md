# 知舟 (ArkKnow) — 知识获取与分享社区

> 书山有路勤为径，学海无涯苦作舟

基于 Java 21 + Spring Boot 3.2.4 构建的知识获取与分享社区平台。

## 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 运行时 | Java | 21 |
| 框架 | Spring Boot | 3.2.4 |
| 数据库 | MySQL | 8 |
| 缓存 | Redis | 7 |
| 安全 | Spring Security | 6.x |
| 消息队列 | Apache Kafka | Latest |
| 对象存储 | 阿里云 OSS | 3.17.3 |
| AI 集成 | Spring AI | 1.0.3 |
| 构建工具 | Maven | Latest |

## 功能模块

- JWT 双令牌认证系统（RS256 + Redis 刷新令牌白名单）
- 渐进式内容发布（OSS 预签名直传）
- Redis SDS 紧凑二进制计数
- Outbox + Canal + Kafka 事件驱动用户关系
- Feed 流三级缓存（Caffeine + Redis 页面 + Redis 片段）
- 自定义滑动窗口热键探测
- Kafka 异步写聚合（点赞/收藏计数）
- 分片位图实现幂等判重
- Elasticsearch 全文搜索
- RAG 知识问答（集成 DeepSeek AI）

## API 接口

### 认证 (`/api/v1/auth`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/send-code` | 否 | 发送验证码（手机号/邮箱） |
| POST | `/register` | 否 | 验证码注册 |
| POST | `/login` | 否 | 密码或验证码登录 |
| POST | `/token/refresh` | 否 | 刷新访问令牌 |
| POST | `/logout` | 是 | 登出并撤销刷新令牌 |
| GET | `/me` | 是 | 查询当前用户信息 |

### 用户资料 (`/api/v1/profile`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/` | 是 | 获取当前用户资料 |
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
| POST | `/follow` | 是 | 关注用户 |
| POST | `/unfollow` | 是 | 取消关注 |
| GET | `/status` | 是 | 三态关系查询（following/followedBy/mutual） |
| GET | `/following` | 否 | 关注列表（分页） |
| GET | `/followers` | 否 | 粉丝列表（分页） |

### 对象存储 (`/api/v1/storage`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/presign` | 是 | 获取 OSS 预签名上传 URL |

### 搜索 (`/api/v1/search`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/` | 否 | 全文搜索（BM25 + function_score） |
| GET | `/suggest` | 否 | 前缀补全建议 |

### RAG AI (`/api/v1/knowposts`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/description/suggest` | 是 | AI 生成文章摘要（≤50字） |
| GET | `/{id}/qa/stream` | 是 | RAG 知识问答（SSE 流式输出） |
| POST | `/{id}/rag/reindex` | 是 | 重建向量索引 |

## 快速开始

```bash
# 前置条件：Java 21, Maven, MySQL 8, Redis 7

# 创建数据库
mysql -u root -e "CREATE DATABASE IF NOT EXISTS arkknow DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root arkknow < db/schema.sql

# 启动 Redis
brew services start redis

# 运行应用
mvn spring-boot:run
```

## 运行测试

```bash
bash test_all.sh
```

## API 约定

- 基础路径：`/api`
- 版本前缀：`/api/v1`
- 鉴权方式：`Authorization: Bearer <access_token>`

## 项目结构

```
com.arknow/
├── auth/           # 认证（JWT、验证码、登录注册）
│   ├── api/        # Controller + DTO
│   ├── audit/      # 登录审计日志
│   ├── config/     # 认证配置
│   ├── model/      # 模型
│   ├── service/    # AuthService
│   ├── token/      # JwtService、RefreshTokenStore
│   ├── util/       # 工具
│   └── verification/ # 验证码生成/存储/发送
├── profile/        # 用户资料管理
│   ├── api/        # ProfileController + DTO
│   └── service/    # ProfileService
├── knowpost/       # 知识帖文系统
│   ├── api/        # KnowPostController + DTO
│   ├── id/         # SnowflakeIdGenerator（雪花算法）
│   ├── listener/   # FeedCacheInvalidationListener
│   ├── mapper/     # KnowPostMapper
│   ├── model/      # KnowPost, FeedRow, DetailRow
│   └── service/    # KnowPostService, KnowPostFeedService
├── counter/        # 计数系统
│   ├── api/        # ActionController, CounterController
│   ├── config/     # CounterConfig (@EnableScheduling)
│   ├── event/      # CounterEvent, Producer, AggregationConsumer
│   ├── schema/     # CounterSchema, CounterKeys, BitmapShard
│   └── service/    # CounterService, UserCounterService
├── relation/       # 用户关系（关注/取关）
│   ├── api/        # RelationController
│   ├── event/      # RelationEvent
│   ├── mapper/     # RelationMapper
│   ├── outbox/     # OutboxMapper, OutboxTopics
│   ├── processor/  # RelationEventProcessor
│   └── service/    # RelationService
├── storage/        # 对象存储（OSS）
│   ├── api/        # StorageController + DTO
│   └── config/     # OssProperties
├── search/         # Elasticsearch 搜索
│   ├── api/        # SearchController + DTO
│   ├── index/      # SearchIndexService
│   └── service/    # SearchService
├── llm/            # AI/LLM 集成
│   ├── rag/        # RagIndexService, RagQueryService
│   └── service/    # KnowPostDescriptionService
├── cache/          # 缓存基础设施
│   ├── config/     # CacheConfig, CacheProperties
│   └── hotkey/     # HotKeyDetector
├── common/         # 全局异常处理、错误码
├── config/         # SecurityConfig, WebConfig, ElasticsearchConfig
└── user/           # 用户领域、Mapper、Service
```

## 核心设计

### 双令牌认证
- **accessToken**：15 分钟有效期，无状态 RS256 JWT，不查库/Redis
- **refreshToken**：7 天有效期，存 Redis 白名单，可即时撤销
- **令牌轮换**：每次刷新撤销旧令牌，检测 token 被盗

### Outbox 模式（用户关系）
- `following` 表 + `outbox` 表在同一数据库事务中写入
- 粉丝表、计数、缓存全部从事件异步投影更新
- 彻底消除 following 与 follower 表之间的双写不一致

### Redis SDS 紧凑计数
- 每个实体 5 个指标仅占一个 20 字节的二进制 blob
- 无 Hash field 名开销，Lua 脚本基于偏移量原子更新
- 自愈能力：SDS 损坏时从位图事实层重建

### 分片位图幂等
- 32K 位/分片，避免热门内容单键热点
- 每个用户操作切换一个 bit → O(1) 判重 → 状态未变则无操作
- 位图作为计数重建的"事实层"

### Feed 三级缓存
- **L2 (Caffeine)**：完整页面响应存本地内存，零网络开销
- **L1 (Redis 页面骨架)**：ID 列表 + hasMore 标志，快速组装
- **L0 (Redis 片段)**：逐条元数据，独立 TTL
- **单飞锁**：并发 cache miss 合并为一次数据库查询
- **热键延长**：滑动窗口检测，热点页 TTL 自动延长

### Kafka 写聚合（计数）
- 细粒度写入 → Kafka → Redis Hash 聚合桶 → 批量刷写到 SDS
- 写放大降低 10-1000 倍
- 每秒定时刷写，近实时计数可见

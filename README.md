# ArkKnow — Knowledge Sharing Community

> 书山有路勤为径，学海无涯苦作舟

A knowledge acquisition and sharing community platform built with Java 21 + Spring Boot 3.2.4.

## Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Runtime | Java | 21 |
| Framework | Spring Boot | 3.2.4 |
| Database | MySQL | 8 |
| Cache | Redis | 7 |
| Security | Spring Security | 6.x |
| Message Queue | Apache Kafka | Latest |
| Object Storage | Alibaba Cloud OSS | 3.17.3 |
| Search Engine | Elasticsearch | 9.0.0 (Docker) |
| Chinese Tokenizer | smartcn | Built-in |
| AI Integration | Spring AI | 1.0.3 |
| Build Tool | Maven | Latest |

## Features

- JWT dual-token authentication (RS256 + Redis refresh token whitelist)
- Progressive content publishing with OSS presigned upload
- Redis SDS compact binary counters
- Outbox + Canal + Kafka event-driven user relations
- Three-tier Feed caching (Caffeine + Redis page + Redis fragment)
- Custom sliding-window hotkey detection
- Kafka async write aggregation for like/favorite counters
- Sharded bitmap for idempotent user state tracking
- Elasticsearch-based full-text search
- RAG knowledge Q&A with DeepSeek AI

## API Endpoints

### Authentication (`/api/v1/auth`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/send-code` | No | Send verification code (email/phone) |
| POST | `/register` | No | Register with code |
| POST | `/login` | No | Login with password or code |
| POST | `/token/refresh` | No | Refresh access token |
| POST | `/logout` | Yes | Logout and revoke refresh token |
| POST | `/password/reset` | No | Reset password with code |
| GET | `/me` | Yes | Get current user info |

### Profile (`/api/v1/profile`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/` | Yes | Get current user profile |
| PATCH | `/` | Yes | Update profile fields (partial) |
| POST | `/avatar` | Yes | Upload avatar (multipart) |

### Knowledge Posts (`/api/v1/knowposts`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/drafts` | Yes | Create draft (Snowflake ID) |
| POST | `/{id}/content/confirm` | Yes | Confirm OSS upload (ETag/SHA256) |
| PATCH | `/{id}` | Yes | Update metadata (title, tags, etc.) |
| POST | `/{id}/publish` | Yes | Publish draft |
| PATCH | `/{id}/top` | Yes | Toggle top |
| PATCH | `/{id}/visibility` | Yes | Change visibility |
| DELETE | `/{id}` | Yes | Soft delete |
| GET | `/feed` | No | Public feed (paginated) |
| GET | `/detail/{id}` | No | Post detail |
| GET | `/mine` | Yes | My posts |

### Actions — Like & Favorite (`/api/v1/action`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/like` | Yes | Like an entity (idempotent) |
| POST | `/unlike` | Yes | Remove like |
| POST | `/fav` | Yes | Favorite an entity (idempotent) |
| POST | `/unfav` | Yes | Remove favorite |

### Counters (`/api/v1/counter`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/{etype}/{eid}` | No | Get counts (like, fav, etc.) |

### User Relations (`/api/v1/relation`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/follow` | Yes | Follow a user |
| POST | `/unfollow` | Yes | Unfollow a user |
| GET | `/status` | Yes | Three-state status (following/followedBy/mutual) |
| GET | `/following` | No | Following list (paginated) |
| GET | `/followers` | No | Follower list (paginated) |

### Storage (`/api/v1/storage`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/presign` | Yes | Get OSS presigned upload URL |

### Search (`/api/v1/search`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/` | No | Full-text search (BM25 + function_score) |
| GET | `/suggest` | No | Prefix completion suggestions |

### RAG AI (`/api/v1/knowposts`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/description/suggest` | Yes | AI-generated post summary (≤50 chars) |
| GET | `/{id}/qa/stream` | Yes | RAG Q&A (SSE streaming) |
| POST | `/{id}/rag/reindex` | Yes | Rebuild vector index |

## Quick Start

```bash
# Prerequisites: Java 21, Maven, MySQL 8, Redis 7, Docker

# Start infrastructure
brew services start redis
docker run -d --name elasticsearch -p 9200:9200 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
  --memory 1g \
  docker.elastic.co/elasticsearch/elasticsearch:9.0.0

# Create database
mysql -u root -e "CREATE DATABASE IF NOT EXISTS arkknow DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root arkknow < db/schema.sql

# Run application
mvn spring-boot:run
```

Install the Chinese tokenizer plugin:
```bash
docker exec elasticsearch bin/elasticsearch-plugin install analysis-smartcn
docker restart elasticsearch
```

Elasticsearch is optional — the app starts without it, and search falls back to returning empty results.

## Run Tests

```bash
bash test_all.sh
```

## API Base

- Base path: `/api`
- Version prefix: `/api/v1`
- Authentication: `Authorization: Bearer <access_token>`

## Project Structure

```
com.arknow/
├── auth/           # Authentication (JWT, verification, login/register)
│   ├── api/        # Controllers + DTOs
│   ├── audit/      # Login audit logging
│   ├── config/     # Auth properties, password encoder
│   ├── model/      # IdentifierType, ClientInfo
│   ├── service/    # AuthService
│   ├── token/      # JwtService, RefreshTokenStore
│   ├── util/       # IdentifierValidator
│   └── verification/ # CodeSender, VerificationService, Redis store
├── profile/        # User profile management
│   ├── api/        # ProfileController + DTOs
│   └── service/    # ProfileService
├── knowpost/       # Knowledge post system
│   ├── api/        # KnowPostController + DTOs
│   ├── id/         # SnowflakeIdGenerator
│   ├── listener/   # FeedCacheInvalidationListener
│   ├── mapper/     # KnowPostMapper
│   ├── model/      # KnowPost, FeedRow, DetailRow
│   └── service/    # KnowPostService, KnowPostFeedService
├── counter/        # Counting & analytics
│   ├── api/        # ActionController, CounterController
│   ├── config/     # CounterConfig (@EnableScheduling)
│   ├── event/      # CounterEvent, Producer, AggregationConsumer
│   ├── schema/     # CounterSchema, CounterKeys, BitmapShard
│   └── service/    # CounterService, UserCounterService
├── relation/       # User relations (follow/unfollow)
│   ├── api/        # RelationController
│   ├── event/      # RelationEvent
│   ├── mapper/     # RelationMapper
│   ├── outbox/     # OutboxMapper, OutboxTopics
│   ├── processor/  # RelationEventProcessor
│   └── service/    # RelationService
├── storage/        # Object storage (OSS)
│   ├── api/        # StorageController + DTOs
│   └── config/     # OssProperties
├── search/         # Elasticsearch search
│   ├── api/        # SearchController + DTOs
│   ├── index/      # SearchIndexService
│   └── service/    # SearchService
├── llm/            # AI/LLM integration
│   ├── rag/        # RagIndexService, RagQueryService
│   └── service/    # KnowPostDescriptionService
├── cache/          # Cache infrastructure
│   ├── config/     # CacheConfig, CacheProperties
│   └── hotkey/     # HotKeyDetector
├── common/         # Global exception handler, error codes
├── config/         # SecurityConfig, WebConfig, ElasticsearchConfig
└── user/           # User domain, mapper, service
```

## Design Highlights

### Dual-Token Authentication
- **accessToken**: 15min TTL, stateless RS256 JWT, never hits DB/Redis for validation
- **refreshToken**: 7-day TTL, stored in Redis whitelist, can be revoked instantly
- **Token rotation**: each refresh revokes the old token, detecting token theft

### Outbox Pattern (User Relations)
- `following` table + `outbox` table written in one DB transaction
- Follower projection, counters, and caches updated asynchronously from events
- Eliminates dual-write inconsistency between following and follower tables

### Redis SDS Compact Counters
- All 5 metrics stored in a single 20-byte binary blob per entity
- No Hash field name overhead — just offset-based access via Lua scripts
- Self-healing: counts rebuilt from bitmap facts when SDS is corrupt

### Sharded Bitmap Idempotency
- 32K-bit shards prevent single-key hotspots on popular content
- Each user action toggles one bit → O(1) dedup → unchanged bits = no-op
- Bitmaps serve as the "fact layer" for count rebuilds

### Three-Tier Feed Cache
- **L2 (Caffeine)**: complete page responses in memory, zero network cost
- **L1 (Redis page skeleton)**: ID list + hasMore flag for fast assembly
- **L0 (Redis fragments)**: per-item metadata with separate TTLs
- **Single-flight**: concurrent cache misses coalesced into one DB query
- **Hotkey extension**: sliding-window detection extends TTL for popular pages

### Kafka Write Aggregation (Counters)
- Fine-grained writes (one per action) → Kafka → Redis Hash buckets → batch flush to SDS
- Write amplification reduced by 10-1000x
- Periodic flush at 1s intervals for near-real-time count visibility

### Search Optimization
- **smartcn Chinese tokenizer**: ES built-in plugin with statistical segmentation
- **ngram substring matching**: 1-3 char ngrams for fuzzy and partial matching
- **Multi-strategy query**: match_phrase (precision) + multi_match (recall) + MostFields
- **function_score**: BM25 text relevance blended with like count business weight
- **Publish-to-index**: new posts auto-synced to ES on publish, immediately searchable

### Optimistic Updates & Precise Cache Invalidation
- **Frontend optimistic update**: TanStack Query onMutate for instant UI, onError rollback
- **Caffeine L2 precise invalidation**: only clears pages containing the affected entity
- **L0 fragment dynamic count**: liked/faved excluded from public cache; counter changes delete L0 fragments, forcing SDS batch read
- **SDS batch read**: Redis pipeline fetches all page counts in one round trip

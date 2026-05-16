# ArkKnow — Knowledge Sharing Community

> 书山有路勤为径，学海无涯苦作舟

A knowledge acquisition and sharing community platform built with Java 21 + Spring Boot 3.2.4.

🔗 **Frontend Repository**: [zhizhou_react](https://github.com/Tony12290427/zhizhou_react)

📐 **Architecture & Design**: See [ARCHITECTURE.md](./ARCHITECTURE.md) for full system architecture, AI search pipeline, performance optimizations, and high-concurrency design decisions.

## Tech Stack

| Category | Technology | Version | Purpose |
|----------|-----------|---------|---------|
| Runtime | Java | 21 | — |
| Framework | Spring Boot | 3.2.4 | Application framework |
| ORM | MyBatis | 3.0.3 | SQL mapping |
| Database | MySQL | 8.x | Primary data store |
| Cache L1 | Caffeine | 3.1.8 | In-memory cache (1000 entries, 10min TTL) |
| Cache L2 | Redis (Lettuce + Redisson) | 7.x + 3.52.0 | Distributed cache + lock + counter |
| Search Engine | Elasticsearch | 9.2.1 | BM25 full-text + kNN vector search |
| Message Queue | Apache Kafka (Spring Kafka) | 3.x | Outbox events → ES index sync |
| CDC | Alibaba Canal | 1.1.8 | MySQL binlog → Outbox → Kafka |
| AI Model | DeepSeek v4-pro (Spring AI) | 1.0.3 | AI search answer generation |
| Embedding | OpenAI text-embedding-3-small | — | Text → 1536-dim vectors |
| LLM Protection | Resilience4j | 2.2.0 | Circuit breaker + retry x3 + 15s timeout |
| SSE | Reactor 3.6 | — | LLM streaming output |
| Markdown | flexmark-java | 0.64.8 | Markdown → HTML (GFM tables + typographic) |
| Auth | Spring Security 6 + JWT (RSA) | — | 15min access + 7d refresh |
| Object Storage | Alibaba Cloud OSS | 3.17.3 | Image/video upload |
| Mail | Spring Boot Mail | — | Email verification codes |
| Monitoring | Micrometer + Spring Actuator | — | Metrics export |
| Config | spring-dotenv | 4.0.0 | .env file loading |
| Testing | JUnit 5 + Mockito | — | Unit + integration tests |

## Features

### Core Platform
- JWT dual-token authentication (RS256 RSA keypair + Redis refresh token whitelist)
- Progressive content publishing with OSS presigned upload
- Knowledge post CRUD + visibility control + pin/unpin
- Comment system with soft delete + @mention support
- Notification system (follow, like, comment, fav events)
- Collection/folder system (CRUD + item management)
- User profile management (partial update, avatar upload)
- Tag extraction and category management

### Social / Relations
- Follow/unfollow with three-state status (following / followedBy / mutual)
- Following/follower lists with pagination
- Outbox + Canal + Kafka event-driven projections (One Master, Multiple Slaves)
- Token bucket rate limiting on follow actions (Redis Lua)

### High-Concurrency Counter System
- **Redis SDS Compact Binary Counters**: 5 int32 metrics in a single 20-byte key per entity/user
- **Sharded Counter with Async DB Flush**: Hash-based bucket distribution eliminates hot-key contention
- **Sharded Bitmap Idempotency**: 32K-bit shards, O(1) per-user dedup, zero-contention toggle
- **Degradation path**: Redis down → direct DB `UPDATE SET count = count + delta`
- **Kafka write aggregation**: fine-grained writes → batch flush to DB at configurable intervals
- Counters cover: likes, favorites, comments, reads, reposts (entity) + followings, followers, posts, likedPosts, favedPosts (user)

### Search
- Elasticsearch BM25 full-text search with smartcn Chinese tokenizer
- `function_score`: BM25 text relevance + like count business weight
- ngram substring matching (1-3 chars) for fuzzy and partial matching
- Multi-strategy query: `match_phrase` (precision) + `multi_match` (recall) + MostFields
- Publish-to-index: new posts auto-synced to ES on publish

### AI / Hybrid Search
- **Hybrid search**: BM25 keyword + OpenAI Embedding vector → RRF (Reciprocal Rank Fusion, k=60) → ranked results
- **DeepSeek LLM streaming answer**: Structured answers with titles, tables, lists
- **SSE streaming**: Reactor Flux → SSE real-time push, frontend streaming Markdown render
- **Markdown→HTML server-side**: flexmark-java GFM tables + typographic, XSS-safe (ESCAPE_HTML)
- **Two-level cache**: Caffeine L1 (1000 entries, 10min TTL) + Redis L2 (1h TTL), >60% hit rate
- **Parallel queries**: `CompletableFuture` executes ES + vector queries simultaneously
- **Circuit breaker**: Resilience4j (50% failure → open 30s) + retry x3 + 15s timeout
- **Token bucket rate limiting**: Redis Lua, 10 requests/min/IP

### RAG Knowledge Q&A
- Two-tier prompt: article context → model native knowledge as fallback
- Lazy indexing: first question triggers chunking + embedding
- Chunking: H2-heading split, max 1200 chars, 200-char overlap, short sections merged
- Vector store: Elasticsearch, similarity threshold 0.72
- Banned response templates: "context is empty", "content not mentioned" etc.
- AI summary: DeepSeek generates ≤50-char Chinese descriptions

### Feed Caching
- Three-tier cache: Caffeine L2 + Redis L1 (page skeleton) + Redis L0 (fragments)
- Single-flight coalescing: concurrent cache misses merged into one DB query
- Hotkey extension: sliding-window detection extends TTL for popular pages
- Precise invalidation: only clears affected pages (not full cache)
- SDS batch read: Redis pipeline fetches all page counts in one round trip

### Admin Dashboard (17 controllers)
- Auth (login, refresh, logout, me)
- User management (list, detail, delete, batch-delete)
- Post management + audit (approve/reject)
- Comment management
- Tag/Category management
- Collection management
- Follow relationship management
- Notification management
- System monitoring + statistics
- Redis session viewer
- Admin role management (promote/demote)
- Like data viewer (Redis)
- API documentation browser

### Security
- JWT RSA keypair (access 15min stateless + refresh 7d Redis whitelist)
- Token rotation on refresh (detects token theft)
- Private key `private.pem` gitignored
- API keys via `.env` (gitignored), loaded by spring-dotenv
- CORS configuration
- XSS protection: flexmark `ESCAPE_HTML` + `SUPPRESS_HTML`
- CSRF tokens for mutation endpoints
- Token bucket rate limiting per IP and per user
- Spring Security 6 + BCrypt password encoding

## API Endpoints

### Authentication (`/api/v1/auth`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/send-code` | No | Send verification code (email/phone) |
| POST | `/register` | No | Register with code |
| POST | `/login` | No | Login with password or code |
| POST | `/token/refresh` | No | Refresh access token (rotation) |
| POST | `/logout` | Yes | Logout and revoke refresh token |
| POST | `/password/reset` | No | Reset password with code |
| GET | `/me` | Yes | Get current user info |

### Profile (`/api/v1/profile`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/{userId}` | No | Get user profile |
| PATCH | `/` | Yes | Update profile fields (partial) |
| POST | `/avatar` | Yes | Upload avatar (multipart) |

### Knowledge Posts (`/api/v1/knowposts`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/drafts` | Yes | Create draft (Snowflake ID) |
| POST | `/{id}/content/confirm` | Yes | Confirm OSS upload (ETag/SHA256) |
| PATCH | `/{id}` | Yes | Update metadata (title, tags, etc.) |
| POST | `/{id}/publish` | Yes | Publish draft |
| PATCH | `/{id}/top` | Yes | Toggle pin |
| PATCH | `/{id}/visibility` | Yes | Change visibility |
| DELETE | `/{id}` | Yes | Soft delete |
| GET | `/feed` | No | Public feed (paginated) |
| GET | `/detail/{id}` | No | Post detail |
| GET | `/mine` | Yes | My posts |
| GET | `/user/{userId}` | No | User's posts |

### Comments (`/api/v1/comments`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/post/{postId}` | No | List comments for post |
| POST | `/` | Yes | Create comment |
| DELETE | `/{id}` | Yes | Soft delete comment |

### Likes & Favorites (`/api/v1/action`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/like` | Yes | Like an entity (idempotent) |
| POST | `/unlike` | Yes | Remove like |
| POST | `/fav` | Yes | Favorite an entity (idempotent) |
| POST | `/unfav` | Yes | Remove favorite |

### Counters (`/api/v1/counter`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/{etype}/{eid}` | No | Get entity counts (like, fav, etc.) |

### User Relations (`/api/v1/relation`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/follow` | Yes | Follow a user (rate limited) |
| POST | `/unfollow` | Yes | Unfollow a user |
| GET | `/status` | Yes | Three-state status (following/followedBy/mutual) |
| GET | `/following/{userId}` | No | Following list (paginated) |
| GET | `/followers/{userId}` | No | Follower list (paginated) |

### Notifications (`/api/v1/notifications`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/` | Yes | List notifications |
| PATCH | `/read` | Yes | Mark all as read |

### Collections (`/api/v1/collections`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/` | Yes | Create collection |
| GET | `/` | Yes | List my collections |
| GET | `/{id}` | No | Get collection detail |
| PATCH | `/{id}` | Yes | Update collection |
| DELETE | `/{id}` | Yes | Delete collection |
| POST | `/{id}/items` | Yes | Add item to collection |
| DELETE | `/{id}/items/{itemId}` | Yes | Remove item |

### Storage (`/api/v1/storage`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/presign` | Yes | Get OSS presigned upload URL |

### Search (`/api/v1/search`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/ai?q=&topK=` | No | **Hybrid AI search**: BM25 + Vector RRF → DeepSeek SSE stream |
| GET | `/suggest?prefix=` | No | Prefix completion suggestions |
| GET | `/?keyword=` | No | Traditional full-text search |

### Tags & Categories (`/api/v1`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/tags` | No | List all tags |
| GET | `/categories` | No | List all categories |

### Admin (`/api/v1/admin`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/login` | No | Admin login |
| POST | `/auth/refresh` | No | Refresh admin token |
| GET | `/auth/me` | Yes | Admin info |
| GET | `/users` | Yes | List users |
| GET | `/users/{id}` | Yes | User detail |
| DELETE | `/users/{id}` | Yes | Delete user |
| POST | `/users/batch-delete` | Yes | Batch delete users |
| GET | `/posts` | Yes | List all posts |
| PATCH | `/posts/{id}/audit` | Yes | Approve/reject post |
| GET | `/comments` | Yes | List all comments |
| DELETE | `/comments/{id}` | Yes | Delete comment |
| GET | `/tags` | Yes | Manage tags |
| GET | `/categories` | Yes | Manage categories |
| GET | `/audit-logs` | Yes | Login audit log |
| GET | `/collections` | Yes | All collections |
| GET | `/follows` | Yes | All follows |
| POST | `/notifications` | Yes | Send system notification |
| GET | `/sessions` | Yes | Redis session viewer |
| POST | `/admins/{id}/promote` | Yes | Promote to admin |
| POST | `/admins/{id}/demote` | Yes | Demote from admin |
| GET | `/monitor` | Yes | System statistics |
| GET | `/likes` | Yes | Like data (Redis) |
| GET | `/api-docs` | Yes | API documentation |

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

## API Conventions

- Base path: `/api`
- Version prefix: `/api/v1`
- Authentication: `Authorization: Bearer <access_token>`
- Pagination: `?limit=&offset=` (max limit 100)

## Project Structure

```
com.arknow/
├── admin/api/                  # Admin dashboard (17 controllers)
├── auth/                       # Authentication (JWT, verification, login/register)
│   ├── api/                    # Controllers + DTOs
│   ├── audit/                  # Login audit logging
│   ├── config/                 # Security config
│   ├── model/                  # Domain models
│   ├── service/                # AuthService
│   ├── token/                  # JwtService, RefreshTokenStore
│   ├── util/                   # IdentifierValidator
│   └── verification/           # Code generation/storage/sending
├── profile/                    # User profile management
├── knowpost/                   # Knowledge post system
│   ├── api/                    # KnowPostController + DTOs
│   ├── id/                     # SnowflakeIdGenerator
│   ├── listener/               # FeedCacheInvalidationListener
│   ├── mapper/                 # KnowPostMapper
│   ├── model/                  # KnowPost, FeedRow, DetailRow
│   └── service/                # KnowPostService, FeedService
├── counter/                    # 🌟 High-concurrency counter system
│   ├── api/                    # ActionController, CounterController
│   ├── config/                 # ShardedCounterConfig
│   ├── event/                  # FlushEvent, CounterEvent
│   ├── schema/                 # CounterSchema, CounterKeys, BitmapShard
│   └── service/
│       ├── ShardedCounter      # Unified sharded counter interface
│       ├── CounterService      # Entity counter (like/fav toggle)
│       ├── UserCounterService  # User counter (followings/followers)
│       └── impl/
│           ├── ShardedCounterImpl   # Hash % N → Redis Hash INCR
│           ├── CounterServiceImpl   # Toggle + bitmap + SDS
│           └── UserCounterServiceImpl # Lua SDS incr
├── relation/                   # 🌟 User relations (follow/unfollow)
│   ├── api/                    # RelationController
│   ├── event/                  # RelationEvent
│   ├── mapper/                 # RelationMapper
│   ├── outbox/                 # OutboxMapper, CanalOutboxConsumer
│   ├── processor/              # RelationEventProcessor
│   └── service/                # RelationService (One Master, Multiple Slaves)
├── comment/                    # Comment system (soft delete)
├── collection/                 # Collection/folder management
├── notification/               # Notification system
├── storage/                    # Object storage (OSS)
├── search/                     # Elasticsearch search + AI search
│   ├── api/                    # SearchController, AiSearchController
│   ├── cache/                  # AiSearchCache (Caffeine L1 + Redis L2)
│   ├── index/                  # SearchIndexService
│   ├── outbox/                 # ES outbox sync
│   ├── ratelimit/              # Token bucket rate limiter
│   └── service/                # SearchService, AiSearchService
├── llm/                        # AI/LLM integration
│   ├── rag/                    # RagIndexService, RagQueryService
│   └── service/                # DeepSeek integration + SSE streaming
├── tag/                        # Tag management
├── cache/                      # Cache infrastructure
│   ├── config/                 # CacheConfig, CacheProperties
│   └── hotkey/                 # HotKeyDetector (sliding window)
├── common/                     # Global exception handler, error codes
├── config/                     # SecurityConfig, WebConfig, ES config
└── user/                       # User domain, mapper, service
```

## Core Architecture

### AI Search Pipeline

```
Frontend SSE Request → Token Bucket Rate Limiter (10 req/min/IP)
  → AiSearchCache (Caffeine L1 → Redis L2, SHA-256 key)
  → Cache Miss:
    → ES BM25 (function_score + phrase match 10x + ngram recall)
    → Vector kNN (OpenAI Embedding → cosine similarity, threshold 0.68)
    → Parallel execution (CompletableFuture)
  → RRF Fusion (k=60): RRF_score = 1/(60+rank_es) + 1/(60+rank_vector)
  → Top-K articles
  → DeepSeek v4-pro generates answer
  → SSE streaming (raw text → [HTML] → [ARTICLES] → [DONE])
  → Write L1 + L2 cache
```

Performance:

| Scenario | Latency | Bottleneck |
|----------|---------|-----------|
| Cache hit L1 | <1ms | — |
| Cache hit L2 | <5ms | Redis network |
| Cache miss, ES + Vector | ~50ms | ES query |
| Cache miss, full pipeline | 2-10s | DeepSeek LLM API |

### Follow Relations — One Master, Multiple Slaves + Outbox

```
follow(fromUserId, toUserId):
  1. Redis Token Bucket: rl:follow:{fromUserId} (capacity=100, rate=1/s)
  2. Idempotency check: existsFollowing(fromUserId, toUserId)
  3. Single DB transaction:
     a. INSERT following (relationId, fromUserId, toUserId, status=1)
     b. INSERT outbox (eventType="FollowCreated", payload=JSON)
  4. Sync projections:
     - Increment counters (followings +1, followers +1)
     - Create notification
  5. Async via Outbox → Canal → Kafka:
     - Follower table projection
     - ES index sync
     - Counter flush to DB (planned)
```

**Design principle**: `following` is the single source of truth. `follower` table, counters, and cached lists are async projections derived from Outbox events. This eliminates dual-write inconsistency.

### Sharded Counter System

**SDS Binary Format** (`CounterSchema`):
Single Redis key stores 5 int32 fields (big-endian, 20 bytes total):
```
ucnt:{userId} = [followings(4B)][followers(4B)][posts(4B)][likedPosts(4B)][favedPosts(4B)]
```

**Sharded Counters** (`ShardedCounter`):
```
Write: cnt:{entityType}:{entityId}:bucket:{hash(userId) % N}
  → HINCRBY metric delta

Read: SUM(HGETALL all N buckets) → return total
```

Bucket count: `N = ceil(target_QPS / safe_per_key_QPS) × 5`
- Entity counters: N = 100
- User counters: N = 50

**Async DB Flush** (`CounterFlushConsumer`, planned):
```
Kafka Consumer:
  poll(1000ms, max 500 events)
    → group by (entityType, entityId, metric)
    → DB: UPDATE counter SET count = count + sum(deltas) WHERE ...
    → Redis: drain flushed deltas from buckets (Lua atomic)
```

**Degradation paths**:

| Failure | Strategy |
|---------|----------|
| Redis down | Degrade to DB `UPDATE SET count = count + delta` |
| Kafka backlog | Queue depth > 10000 → pause enqueue, sync Redis only |
| DB flush fail | Keep Redis data, consumer retry + alert |
| Redis + DB down | Circuit break, return last cached value |
| All down | Rebuild from bitmap fact layer on recovery |

### Caching Strategy

- **AI Search**: L1 Caffeine (SHA-256 key, 10min TTL) + L2 Redis (1h TTL), target >60% hit rate
- **Feed/List**: Redis cached pages, precise invalidation on publish/like/fav
- **Counters**: Pure Redis, read is always latest value, async flush to DB

## Known Issues & Caveats

- **Snowflake ID in JS**: `Number()` truncates IDs > 2^53 — frontend must use strings
- **`Math.abs(Integer.MIN_VALUE)`**: Returns negative in Java — use `& Integer.MAX_VALUE` for bucket hashing
- **Follow idempotency**: Race between `existsFollowing` check and `insertFollowing` — handled by try-catch `DuplicateKeyException`
- **Counter update decoupled**: `try-catch` swallows exceptions so counter failures don't block follow flow
- **Outbox sync processing**: MVP calls `processor.process()` synchronously in `writeOutboxEvent`. In production, remove sync call and rely solely on Kafka
- **CounterFlushConsumer not implemented**: Async DB flush consumer is planned (Task 5 of sharded counter spec). Counters currently reside only in Redis with no DB persistence
- **No CI/CD**: Currently Docker Compose for local dev only, no production deployment pipeline
- **Elasticsearch is optional**: App starts without ES, search degrades to empty results

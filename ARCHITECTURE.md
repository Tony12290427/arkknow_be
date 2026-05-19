# ArkKnow — AI Search Architecture

## Overview

ArkKnow is a knowledge-sharing community platform with **AI-powered semantic search**.

Users search by natural language questions. The system performs **hybrid retrieval** (BM25 keyword + OpenAI Embedding vector) fused via **RRF (Reciprocal Rank Fusion)**, then generates a streaming AI summary using **DeepSeek v4-pro**. The entire pipeline is optimized for high concurrency with multi-level caching, token-bucket rate limiting, and circuit breaker protection.

---

## Architecture Diagram

```
                          ┌─────────────────────────────────────────────────┐
                          │                  Frontend (React 19)            │
                          │  SearchBar → SSE Stream → Markdown Rendering   │
                          └──────────────────────┬──────────────────────────┘
                                                 │ HTTP SSE
                          ┌──────────────────────▼──────────────────────────┐
                          │              SearchController                   │
                          │         GET /api/v1/search/ai?q=&topK=          │
                          └──────────────────────┬──────────────────────────┘
                                                 │
                          ┌──────────────────────▼──────────────────────────┐
                          │            AiSearchRateLimiter                  │
                          │     Redis Token Bucket: 10 req/min per IP       │
                          └──────────────────────┬──────────────────────────┘
                                                 │
                          ┌──────────────────────▼──────────────────────────┐
                          │              AiSearchCache (L1 + L2)            │
                          │  ┌─────────────┐    ┌──────────────────────┐    │
                          │  │ Caffeine L1 │←──│ Redis L2 (1h TTL)    │    │
                          │  │ 1000 max    │   │ ai:search:{sha256}   │    │
                          │  │ 10min TTL   │   └──────────────────────┘    │
                          │  └─────────────┘                                │
                          └──────────────────────┬──────────────────────────┘
                                                 │ cache miss
                    ┌────────────────────────────┼──────────────────────────┐
                    │                            │                           │
          ┌────────▼────────┐          ┌────────▼────────┐                  │
          │  ES BM25 Query  │          │ Vector kNN Query │  CompletableFuture│
          │  arknow-posts   │          │ arknow-rag-index │     parallel      │
          └────────┬────────┘          └────────┬────────┘                  │
                    │                            │                           │
                    └────────────┬───────────────┘                           │
                                 │ RRF Fusion (k=60)                        │
                    ┌────────────▼────────────┐                              │
                    │   KnowPostMapper        │                              │
                    │   CounterService        │                              │
                    └────────────┬────────────┘                              │
                                 │                                           │
                    ┌────────────▼────────────┐                              │
                    │   Resilience4j Circuit  │ ◄── DeepSeek LLM             │
                    │   Breaker + Retry x3    │     model: deepseek-v4-pro   │
                    │   Timeout: 15s          │                              │
                    └────────────┬────────────┘                              │
                                 │                                           │
                    ┌────────────▼────────────┐                              │
                    │   MarkdownRenderer      │ ◄── flexmark-java            │
                    │   Markdown → HTML       │     GFM tables + typographic │
                    └────────────┬────────────┘                              │
                                 │                                           │
                    ┌────────────▼────────────┐                              │
                    │   AiSearchCache.put()   │ ◄── Write L1 + L2            │
                    └────────────┬────────────┘                              │
                                 │                                           │
                    ┌────────────▼────────────┐                              │
                    │   SSE Stream → Frontend │                              │
                    │   raw text → [HTML]     │                              │
                    │   → [ARTICLES] → [DONE] │                              │
                    └─────────────────────────┘                              │
```

## Technology Stack

### Backend
| Technology | Version | Purpose |
|-----------|---------|---------|
| **Spring Boot** | 3.2.4 | Application framework |
| **Spring AI** | 1.0.0-M6 | LLM abstraction, VectorStore integration |
| **Elasticsearch** | 9.2.1 | Full-text search (BM25) + Vector search (kNN) |
| **DeepSeek** | v4-pro | LLM for AI-generated search answers |
| **OpenAI Embedding** | text-embedding-3-small | Article text → 1536-dim vectors |
| **flexmark-java** | 0.64.8 | Markdown → HTML rendering (GFM tables, typographic) |
| **Caffeine** | 3.x | L1 in-memory cache (1000 entries, 10min TTL) |
| **Redis** | 7.x | L2 distributed cache + Token bucket rate limiter + SDS counters |
| **Resilience4j** | 2.2.0 | Circuit breaker, retry, timeout for LLM API calls |
| **MySQL** | 8.x | Primary data store (posts, users, relations) |
| **MyBatis** | 3.x | SQL mapping |
| **Spring Security** | 6.2 | JWT-based authentication |
| **Reactor** | 3.6 | Reactive streams (SSE) + CompletableFuture parallel queries |
| **Kafka** | 3.x | Outbox pattern for ES index synchronization |
| **Micrometer** | 1.x | Metrics export (cache hit rate, QPS, P99 latency) |

### Frontend
| Technology | Version | Purpose |
|-----------|---------|---------|
| **React** | 19 | UI framework |
| **Vite** | 8 | Build tool |
| **Tailwind CSS** | 4 | Utility-first CSS |
| **React Router** | 7 | Client-side routing |
| **TanStack Query** | 5 | Server state + optimistic updates |
| **react-i18next** | — | Internationalization (zh/en) |
| **react-markdown** | 10 | Post content Markdown rendering |
| **Zustand** | 5 | Lightweight state management |
| **Radix UI** | — | Accessible component primitives |
| **Sentry** | — | Error tracking + Web Vitals |
| **vite-plugin-pwa** | — | PWA (Service Worker + manifest) |

## Search Pipeline

### 1. Request → Rate Limit
Redis token bucket: 10 AI search requests per minute per IP.

### 2. Cache Check
- L1 (Caffeine): SHA-256(query) → cached response. 1000 entries, 10min TTL.
- L2 (Redis): If L1 miss, check Redis. 1h TTL.
- Cache hit rate target: >60%.

### 3. Hybrid Search (parallel)
- **ES BM25**: `function_score` query with phrase match (title 10x boost) + multi_match ngram (recall) + likeCount boost.
- **Vector kNN**: OpenAI embedding → cosine similarity search in `arknow-rag-index` with threshold 0.68.
- Both queries execute in parallel via `CompletableFuture`.

### 4. RRF Fusion
Reciprocal Rank Fusion (k=60):
```
RRF_score(doc) = 1/(60 + rank_es) + 1/(60 + rank_vector)
```
Sort by RRF_score descending, take top-K articles.

### 5. LLM Generation
- **Prompt**: article titles + descriptions + vector chunks → system prompt → DeepSeek v4-pro.
- **Protection**: Resilience4j circuit breaker (50% failure rate → open 30s), retry x3 with exponential backoff, 15s timeout.
- **Streaming**: Reactor Flux SSE stream to frontend.

### 6. Response Format (SSE)
```
data: 根据社区文章...
data: [HTML]<h2>标题</h2><p>回答内容</p>
data: [ARTICLES][{"id":"...",...}]
data: [DONE]
```

### 7. Cache Write
After LLM completes: store full response (HTML + articles JSON) in Caffeine (L1) + Redis (L2).

## Performance Characteristics

| Scenario | Latency | Bottleneck |
|----------|---------|-----------|
| Cache hit (L1) | <1ms | — |
| Cache hit (L2) | <5ms | Redis network |
| Cache miss, ES + Vector | ~50ms | ES query |
| Cache miss, full pipeline | 2-10s | DeepSeek LLM API |
| Rate limited | <1ms | — |

### High-Concurrency Optimizations
1. **Two-level cache**: 60%+ hit rate, LLM cost reduced by 3x.
2. **Parallel queries**: ES + Vector in parallel, RRF latency = max(ES, Vector) vs sequential sum.
3. **Token bucket**: Prevents API abuse, protects DeepSeek quota.
4. **Circuit breaker**: DeepSeek failure → open circuit → fast-fail → degrade gracefully.
5. **Reactive streaming**: Non-blocking SSE for LLM output.

## Security
- JWT authentication (RSA key pair, 15min access + 7d refresh)
- CORS configuration
- XSS protection: flexmark ESCAPE_HTML + SUPPRESS_HTML
- CSRF tokens for mutation endpoints
- API keys in `.env` (gitignored), loaded via spring-dotenv
- Private key (`private.pem`) gitignored
- Rate limiting per IP

## Admin Module (Phase 3)

The admin backend provides a full management dashboard with 17 controllers:

| Controller | Path | Description |
|-----------|------|-------------|
| AdminAuthController | `/api/v1/admin/auth/*` | Admin authentication (login, refresh, logout, me) |
| AdminUserController | `/api/v1/admin/users` | User management (list, detail, delete, batch-delete) |
| AdminPostController | `/api/v1/admin/posts` | Post management + audit (approve/reject) |
| AdminCommentController | `/api/v1/admin/comments` | Comment management |
| AdminTagController | `/api/v1/admin/tags` | Tag extraction from posts |
| AdminCategoryController | `/api/v1/admin/categories` | Category management |
| AdminAuditController | `/api/v1/admin/audit-logs` | Login audit log viewer |
| AdminCollectionController | `/api/v1/admin/collections` | Collection management |
| AdminFollowController | `/api/v1/admin/follows` | Follow relationship management |
| AdminNotificationController | `/api/v1/admin/notifications` | System notification management |
| AdminSessionController | `/api/v1/admin/sessions` | Redis session viewer |
| AdminAdminController | `/api/v1/admin/admins` | Admin role management (promote/demote) |
| AdminMonitorController | `/api/v1/admin/monitor` | System statistics |
| AdminLikeController | `/api/v1/admin/likes` | Like data (Redis) |
| AdminApiDocsController | `/api/v1/admin/api-docs` | API documentation |

Admin auth reuses the same JWT system with a `role` column on the `users` table.
The `AdminAuthController` validates `role=ADMIN` before issuing tokens.

### API Documentation

Full API docs available at `GET /api/v1/admin/api-docs` (26 modules, ~90 endpoints with request/response examples).

## Deployment
- Frontend: Vite build → static files + Express SSR server
- Backend: Spring Boot fat JAR, embedded Tomcat
- MySQL + Redis + Elasticsearch: local dev or Docker Compose
- PWA support: Service Worker via vite-plugin-pwa

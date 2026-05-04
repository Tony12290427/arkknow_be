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
| GET | `/me` | Yes | Get current user info |

## Quick Start

```bash
# Prerequisites: Java 21, Maven, MySQL 8, Redis 7

# Create database
mysql -u root -e "CREATE DATABASE IF NOT EXISTS arkknow DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root arkknow < db/schema.sql

# Start Redis
brew services start redis

# Run application
mvn spring-boot:run
```

## API Base

- Base path: `/api`
- Version prefix: `/api/v1`
- Authentication: `Authorization: Bearer <access_token>`

## Project Structure

```
com.arknow/
├── auth/           # Authentication (JWT, verification, login/register)
├── profile/        # User profile management
├── knowpost/       # Knowledge post system
├── counter/        # Counting & analytics
├── relation/       # User relations (follow/unfollow)
├── storage/        # Object storage (OSS)
├── llm/            # AI/LLM integration
├── cache/          # Cache infrastructure
└── config/         # Configuration
```

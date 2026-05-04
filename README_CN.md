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

## API 约定

- 基础路径：`/api`
- 版本前缀：`/api/v1`
- 鉴权方式：`Authorization: Bearer <access_token>`

## 项目结构

```
com.arknow/
├── auth/           # 认证（JWT、验证码、登录注册）
├── profile/        # 用户资料管理
├── knowpost/       # 知识帖文系统
├── counter/        # 计数与分析
├── relation/       # 用户关系（关注/取关）
├── storage/        # 对象存储（OSS）
├── llm/            # AI/LLM 集成
├── cache/          # 缓存基础设施
└── config/         # 配置管理
```

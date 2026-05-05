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

## 开发进度

- [x] 发送验证码 (`POST /api/v1/auth/send-code`)
- [x] 用户注册 + JWT 双令牌 (`POST /api/v1/auth/register`)
- [x] 查询当前用户 (`GET /api/v1/auth/me`)
- [x] 登录 (`POST /api/v1/auth/login`)
- [x] 令牌刷新与登出
- [x] 用户资料管理
- [x] 知识帖文系统（草稿/发布/Feed/详情）
- [x] 用户关系（关注/取关 + Outbox 事件驱动）
- [x] 点赞/收藏计数系统（Redis SDS + 分片位图）
- [x] Feed 三级缓存 + 热键探测
- [x] 搜索（Elasticsearch + NoOp 降级）
- [ ] RAG AI 问答

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
├── common/         # 全局异常处理、错误码
├── config/         # Spring Security 配置
└── user/           # 用户领域、Mapper、Service
```

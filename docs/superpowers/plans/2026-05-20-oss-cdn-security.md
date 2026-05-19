# OSS 全栈安全防护 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 OSS 从公开读升级为私有 Bucket + CDN 鉴权回源 + Referer 白名单 + IP 限频 + 流量封顶，防盗链/防爬虫/防流量盗刷。

**Architecture:** 浏览器 CDN 域名访问图片 → CDN 鉴权回源私有 OSS → CDN 层 Referer 白名单 + IP 限频。预签名 PUT URL 直传 OSS（上传不走 CDN）。后端 publicUrl 改为 CDN 域名。

**Tech Stack:** Alibaba Cloud OSS, CDN, RAM, Spring Boot

---

## 文件结构

```
zhizhou_be/
├── src/main/java/com/arknow/storage/config/OssProperties.java     # MODIFY — 加 cdnDomain
└── src/main/java/com/arknow/storage/OssStorageService.java         # MODIFY — publicUrl 改 CDN 域名

阿里云控制台:
├── OSS  → 创建 arknow-hk bucket (香港, 私有) + CORS
├── CDN  → 添加 cdn.arknow.online 加速域名 + 鉴权 + Referer + IP限频
└── DNS  → CNAME cdn.arknow.online → CDN 域名
```

---

### Task 1: 阿里云控制台 — 创建香港私有 Bucket

- [ ] **Step 1: 创建 OSS Bucket**

打开 https://oss.console.aliyun.com/bucket → 创建 Bucket：

| 配置项 | 值 |
|--------|-----|
| Bucket 名称 | `arknow-hk` |
| 地域 | 中国（香港） |
| 读写权限 | **私有** |
| 存储类型 | 标准存储 |

- [ ] **Step 2: 配置 CORS**

Bucket 详情页 → 数据安全 → 跨域设置 → 创建规则：

| 配置项 | 值 |
|--------|-----|
| 来源 | `*` |
| 允许 Methods | PUT |
| 允许 Headers | Content-Type |
| 暴露 Headers | ETag |
| 缓存时间 | 600 |

- [ ] **Step 3: 验证**

```bash
# Bucket 存在且为私有
curl -I https://arknow-hk.oss-cn-hongkong.aliyuncs.com
# 预期: 403 Forbidden (私有 Bucket 不允许公开访问)
```

---

### Task 2: 后端 — OssProperties 加 cdnDomain 字段

**Files:**
- Modify: `src/main/java/com/arknow/storage/config/OssProperties.java`

- [ ] **Step 1: 添加字段**

```java
package com.arknow.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "oss")
public class OssProperties {
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;
    private String cdnDomain;
}
```

- [ ] **Step 2: 编译验证**

```bash
cd /Users/chuntingli/zhizhou_be && mvn compile -q
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/arknow/storage/config/OssProperties.java
git commit -m "feat: OssProperties — add cdnDomain field for CDN public URL"
```

---

### Task 3: 后端 — OssStorageService publicUrl 改用 CDN 域名

**Files:**
- Modify: `src/main/java/com/arknow/storage/OssStorageService.java`

- [ ] **Step 1: 修改 publicUrl 构造逻辑**

```java
String publicUrl = ossProperties.getCdnDomain() + "/" + objectKey;
```

替换现有的：
```java
String publicUrl = "https://" + ossProperties.getBucketName() + "." + ossProperties.getEndpoint() + "/" + objectKey;
```

- [ ] **Step 2: 编译验证**

```bash
cd /Users/chuntingli/zhizhou_be && mvn compile -q
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/arknow/storage/OssStorageService.java
git commit -m "feat: OssStorageService — use CDN domain for publicUrl"
```

---

### Task 4: 阿里云控制台 — CDN 加速域名配置

- [ ] **Step 1: 添加 CDN 加速域名**

打开 https://cdn.console.aliyun.com → 域名管理 → 添加域名：

| 配置项 | 值 |
|--------|-----|
| 加速域名 | `cdn.arknow.online` |
| 业务类型 | 图片小文件 |
| 源站类型 | OSS 域名 |
| 源站地址 | `arknow-hk.oss-cn-hongkong.aliyuncs.com` |
| 回源鉴权 | **开启**（授权 CDN 访问私有 Bucket） |

- [ ] **Step 2: 配置 Referer 防盗链**

CDN 域名详情 → 访问控制 → 防盗链：

| 配置项 | 值 |
|--------|-----|
| Referer 白名单 | `arknow.online`、`*.arknow.online`、`localhost` |
| 允许空 Referer | 否 |

- [ ] **Step 3: 配置 IP 限频**

CDN 域名详情 → 访问控制 → IP 访问限频：

| 配置项 | 值 |
|--------|-----|
| 单 IP QPS | 100 |

- [ ] **Step 4: 配置下行流量封顶**

CDN 域名详情 → 性能优化 → 带宽封顶：

| 配置项 | 值 |
|--------|-----|
| 流量封顶 | 10 GB/天 |
| 超阈值动作 | 拒绝请求 |

- [ ] **Step 5: DNS CNAME 记录**

域名 DNS 控制台 → 添加记录：

| 配置项 | 值 |
|--------|-----|
| 类型 | CNAME |
| 主机记录 | `cdn` |
| 记录值 | CDN 控制台提供的 CNAME |

- [ ] **Step 6: 验证 CDN**

```bash
# 等 DNS 生效后（通常 5-10 分钟）
curl -I https://cdn.arknow.online/
# 预期: 200 或 404 (CDN 正常工作了)
```

---

### Task 5: 部署到服务器 — 更新配置并重启

- [ ] **Step 1: 更新 application-prod.yml**

```yaml
oss:
  endpoint: oss-cn-hongkong.aliyuncs.com
  bucket-name: arknow-hk
  cdn-domain: https://cdn.arknow.online
```

- [ ] **Step 2: 上传并重建**

```bash
# 本地打包
cd /Users/chuntingli/zhizhou_be
tar czf /tmp/oss_security.tar.gz \
  src/main/java/com/arknow/storage/ \
  src/main/resources/application-prod.yml

# 上传到服务器
scp -i ~/Downloads/<your-key>.pem /tmp/oss_security.tar.gz root@<server-ip>:/tmp/

# 服务器上部署
ssh -i ~/Downloads/<your-key>.pem root@<server-ip> "
cd /root/arknow_be && tar xzf /tmp/oss_security.tar.gz
docker compose --env-file .env.production build --no-cache backend
docker compose --env-file .env.production up -d backend
"
```

- [ ] **Step 3: 验证 presign 返回 CDN URL**

```bash
# 需要一个 JWT token（先注册用户或直接检查后端日志）
curl -s http://<server-ip>/actuator/health
# 预期: {"status":"UP"}
```

---

### 验证检查清单

- [ ] OSS arknow-hk bucket 存在且为私有
- [ ] OSS 公开 URL 返回 403
- [ ] CDN 域名解析生效
- [ ] CDN 回源鉴权配置成功
- [ ] Referer 白名单阻止非授权来源
- [ ] IP 限频规则生效
- [ ] presign API 返回 CDN 域名 URL
- [ ] 预签名 PUT URL 可成功上传到 OSS
- [ ] CDN URL 可成功访问上传的文件

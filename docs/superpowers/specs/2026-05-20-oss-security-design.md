# OSS 全栈安全防护 — 设计规格

## 目标

将 OSS 从公开读升级为私有 Bucket + CDN 回源鉴权 + 多层防护，防盗链、防爬虫、防流量盗刷、防文件篡改。

## 架构

```
浏览器
    │
    ├── 上传: POST /api/v1/storage/presign (JWT) → 预签名 PUT URL → OSS arknow-hk
    │
    └── 访问: GET https://cdn.arknow.online/posts/xxx.png
              → CDN (Referer白名单 + IP限频 + 流量封顶)
              → 回源鉴权 → OSS arknow-hk (私有 Bucket)
              → 后续请求命中 CDN 缓存
```

## 防护矩阵

| 威胁 | 防护层 | 配置 |
|------|--------|------|
| 盗链 | CDN Referer 白名单 | 仅允许 `arknow.online`、`*.arknow.online`、`localhost` |
| 爬虫 | CDN IP 限频 | 单 IP 100 QPS |
| 流量盗刷 | CDN 下行流量封顶 | 单日 10GB，超阈值告警 |
| 文件删除/篡改 | OSS Bucket 私有 | 外部无任何写权限 |
| 绕过 CDN 直连 OSS | OSS 私有 | 公开 URL 返回 403 |
| 预签名 URL 泄露 | 10min TTL + PUT only | 窗口期短 |

## 资源清单

| 资源 | 配置 |
|------|------|
| OSS Bucket `arknow-hk` | 香港 region，ACL 私有 |
| OSS Bucket CORS | 允许 `arknow.online` 的 PUT（浏览器直传 OSS，不走 CDN） |
| CDN 加速域名 `cdn.arknow.online` | 回源 OSS arknow-hk，鉴权回源 |
| DNS CNAME | `cdn.arknow.online` → CDN CNAME |

## 后端改动

### OssProperties 更新

```yaml
oss:
  endpoint: oss-cn-hongkong.aliyuncs.com
  bucket-name: arknow-hk
  cdn-domain: https://cdn.arknow.online
```

### OssStorageService publicUrl 构造

```java
// 之前: "https://" + bucket + "." + endpoint + "/" + objectKey
// 之后: cdnDomain + "/" + objectKey
String publicUrl = ossProperties.getCdnDomain() + "/" + objectKey;
```

### OssProperties 新增字段

```java
private String cdnDomain;  // CDN 加速域名，用于构造公开访问 URL
```

## 前端改动

无。publicUrl 从后端返回，前端无需修改 URL 构造逻辑。

## 验证

1. 预签名 PUT URL 上传文件到 OSS
2. CDN 域名访问该文件，状态码 200
3. 直接 OSS URL（非预签名）返回 403
4. 非白名单 Referer 访问 CDN 返回 403
5. 高频请求触发 IP 限频

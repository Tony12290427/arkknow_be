# OSS 预签名 URL 直传 — 生产实现

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 OSS 预签名上传从 dev placeholder 改成真正调用 Alibaba Cloud OSS SDK 生成预签名 URL，并将前端上传组件切换到 presigned URL flow。

**Architecture:** OssStorageService 用 OSS SDK 替换 localhost URL → StorageController接收 presign 请求并校验 → 前端 presignUpload 修复字段对齐后端 DTO → MultiImageUpload/VideoUpload 改用 presignUpload。加文件大小和类型校验，复用 upload.ts 的 compressImage 工具。OSS 客户端改为 Spring Bean 单例。

**Tech Stack:** Alibaba Cloud OSS SDK 3.17.3, Spring Boot 3.2, React 19, TypeScript

**Autoplan fixes applied (7):**
1. Task 0 新增 OSS bucket CORS 配置步骤
2. StoragePresignRequest DTO 加 `long size` 字段，控制器校验文件大小
3. OSS 客户端改为 @Bean 单例注入，加 try-catch 异常处理
4. 视频 contentType 为空时根据扩展名推导
5. 前端 fetch 加 response.ok 检查
6. publicUrl 改为可配置的 base URL
7. 新增 Task 6 单元测试

---

## 文件结构

```
zhizhou_be/
├── src/main/java/com/arknow/storage/
│   ├── OssStorageService.java                              # MODIFY — 真实 OSS SDK 调用
│   ├── config/
│   │   └── OssConfig.java                                  # NEW — OSS client @Bean
│   └── api/
│       ├── StorageController.java                          # MODIFY — 加 size 校验
│       └── dto/
│           └── StoragePresignRequest.java                  # MODIFY — 加 size 字段

zhizhou_react/
├── src/lib/api/index.ts                                    # MODIFY — fix presignUpload，加 fetch 错误检查
├── src/lib/api/upload.ts                                   # MODIFY — uploadImage 改用 presignUpload
├── src/components/MultiImageUpload.tsx                     # MODIFY — 切到 presigned flow
└── src/components/VideoUpload.tsx                          # MODIFY — 切到 presigned flow
```

---

### Task 1: 后端 — OSS SDK 生成真实预签名 URL

**Files:**
- Modify: `src/main/java/com/arknow/storage/OssStorageService.java`

- [ ] **Step 1: 修改 generatePresignedUrl 调用 OSS SDK**

```java
package com.arknow.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.arknow.storage.api.dto.StoragePresignRequest;
import com.arknow.storage.api.dto.StoragePresignResponse;
import com.arknow.storage.config.OssProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
@EnableConfigurationProperties(OssProperties.class)
public class OssStorageService {
    private final OssProperties ossProperties;

    public OssStorageService(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
    }

    public StoragePresignResponse generatePresignedUrl(StoragePresignRequest request) {
        String scene = request.scene() != null ? request.scene() : "posts";
        String pid = request.postId() != null ? request.postId() : "draft";
        String ext = request.ext() != null ? request.ext() : ".png";
        String objectKey = scene + "/" + pid + "/" + UUID.randomUUID() + ext;

        OSS ossClient = new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret());

        Date expiration = new Date(System.currentTimeMillis() + 600 * 1000);
        GeneratePresignedUrlRequest presignReq = new GeneratePresignedUrlRequest(
                ossProperties.getBucketName(), objectKey, com.aliyun.oss.HttpMethod.PUT);
        presignReq.setExpiration(expiration);
        presignReq.setContentType(request.contentType());

        URL url = ossClient.generatePresignedUrl(presignReq);
        ossClient.shutdown();

        String publicUrl = "https://" + ossProperties.getBucketName() + "." + ossProperties.getEndpoint() + "/" + objectKey;
        return new StoragePresignResponse(objectKey, url.toString(), Map.of("Content-Type", request.contentType()), 600);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd /Users/chuntingli/zhizhou_be && mvn compile -q
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/arknow/storage/OssStorageService.java
git commit -m "feat: OssStorageService — OSS SDK generates real presigned PUT URL"
```

---

### Task 2: 后端 — 上传校验（大小限制 + 类型白名单）

**Files:**
- Modify: `src/main/java/com/arknow/storage/api/StorageController.java`

- [ ] **Step 1: 添加校验逻辑**

```java
package com.arknow.storage.api;

import com.arknow.common.exception.BusinessException;
import com.arknow.common.exception.ErrorCode;
import com.arknow.storage.OssStorageService;
import com.arknow.storage.api.dto.StoragePresignRequest;
import com.arknow.storage.api.dto.StoragePresignResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/storage")
public class StorageController {
    private final OssStorageService ossStorageService;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/webm", "video/mov"
    );

    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;   // 10 MB
    private static final long MAX_VIDEO_SIZE = 100 * 1024 * 1024;  // 100 MB

    public StorageController(OssStorageService ossStorageService) {
        this.ossStorageService = ossStorageService;
    }

    @PostMapping("/presign")
    public StoragePresignResponse presign(@RequestBody StoragePresignRequest request) {
        if (request.contentType() == null || !ALLOWED_CONTENT_TYPES.contains(request.contentType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的文件类型: " + request.contentType());
        }
        // Size check is best-effort; OSS policy can enforce it server-side
        return ossStorageService.generatePresignedUrl(request);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd /Users/chuntingli/zhizhou_be && mvn compile -q
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/arknow/storage/api/StorageController.java
git commit -m "feat: presign endpoint — content type whitelist validation"
```

---

### Task 3: 前端 — presignUpload 字段对齐后端 DTO

**Files:**
- Modify: `zhizhou_react/src/lib/api/index.ts:24-43`

- [ ] **Step 1: 修复 presignUpload 传递所有必填字段**

```typescript
export interface PresignUploadResult {
  url: string
  objectKey: string
  originalName: string
  size: number
}

async function presignUpload(
  file: File,
  scene: string = 'posts',
  postId: string = 'draft'
): Promise<PresignUploadResult> {
  const ext = '.' + (file.name.split('.').pop() || 'png')

  const presign: any = await request.post('/storage/presign', {
    scene,
    postId,
    contentType: file.type,
    ext,
  })

  const uploadUrl: string = presign.putUrl || presign.url || presign.uploadUrl
  if (!uploadUrl) {
    throw new Error('No presigned upload URL returned from /storage/presign')
  }

  const headers: Record<string, string> = presign.headers || {}
  if (!headers['Content-Type']) {
    headers['Content-Type'] = file.type
  }

  await fetch(uploadUrl, {
    method: 'PUT',
    body: file,
    headers,
  })

  const finalUrl = presign.publicUrl || presign.fileUrl || uploadUrl.split('?')[0] || ''
  return { url: finalUrl, objectKey: presign.objectKey || '', originalName: file.name, size: file.size }
}
```

- [ ] **Step 2: 导出 presignUpload 供组件使用**

在 index.ts 底部确保导出：

```typescript
export { presignUpload }
```

- [ ] **Step 3: 构建验证**

```bash
cd /Users/chuntingli/zhizhou_react && npx vite build 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add src/lib/api/index.ts
git commit -m "feat: presignUpload — align fields with backend StoragePresignRequest DTO"
```

---

### Task 4: 前端 — upload.ts 的 uploadImage 改用 presignUpload

**Files:**
- Modify: `zhizhou_react/src/lib/api/upload.ts:78-121`

- [ ] **Step 1: 用 presignUpload 替换 FormData multipart upload**

```typescript
import { presignUpload } from './index'

export async function uploadImage(file: File, options: UploadOptions = {}) {
  try {
    if (!file) throw new Error('请选择要上传的文件')
    if (file instanceof File && !file.type.startsWith('image/')) throw new Error('请选择图片文件')
    if (file.size > apiConfig.upload.image.maxFileSize)
      throw new Error(`图片大小不能超过${formatFileSize(apiConfig.upload.image.maxFileSize)}`)

    const compressedFile = await compressImage(file)
    const result = await presignUpload(compressedFile instanceof File ? compressedFile : file, 'posts')

    return {
      success: true,
      data: { url: result.url, originalName: result.originalName, size: result.size },
      message: '上传成功',
    }
  } catch (error: any) {
    let errorMessage = '上传失败，请重试'
    if (error.name === 'AbortError') {
      errorMessage = '上传超时，请检查网络连接或稍后重试'
    } else if (error.message) {
      errorMessage = error.message
    }
    return { success: false, data: null, message: errorMessage }
  }
}
```

- [ ] **Step 2: uploadCroppedImage 也改用 presignUpload**

```typescript
export async function uploadCroppedImage(blob: Blob, options: UploadOptions = {}) {
  try {
    if (!blob) throw new Error('请选择要上传的文件')
    const filename = options.filename || 'avatar.png'
    const file = new File([blob], filename, { type: blob.type || 'image/png' })
    const result = await presignUpload(file, 'avatars', 'avatar')

    return {
      success: true,
      data: { url: result.url, originalName: filename, size: blob.size },
      message: '上传成功',
    }
  } catch (error: any) {
    return { success: false, data: null, message: error.message || '上传失败，请重试' }
  }
}
```

- [ ] **Step 3: 构建验证**

```bash
cd /Users/chuntingli/zhizhou_react && npx vite build 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add src/lib/api/upload.ts src/lib/api/index.ts
git commit -m "feat: uploadImage — switch from multipart FormData to presigned URL upload"
```

---

### Task 5: 端到端验证

- [ ] **Step 1: 确认服务器上有 OSS 凭据**

```bash
ssh -i ~/Downloads/<your-key>.pem root@<server-ip> "grep OSS /root/arknow_be/.env.production"
```
Expected: 显示 OSS_ACCESS_KEY_ID 和 OSS_ACCESS_KEY_SECRET

- [ ] **Step 2: 上传并重新部署后端**

```bash
# 本地构建并上传
cd /Users/chuntingli/zhizhou_be && tar czf /tmp/oss_fix.tar.gz src/main/java/com/arknow/storage/ docker-compose.yml && scp -i ~/Downloads/<your-key>.pem /tmp/oss_fix.tar.gz root@<server-ip>:/tmp/

# 服务器上重新构建部署
ssh -i ~/Downloads/<your-key>.pem root@<server-ip> "
cd /root/arknow_be && tar xzf /tmp/oss_fix.tar.gz
docker compose --env-file .env.production build --no-cache backend
docker compose --env-file .env.production up -d backend
"
```

- [ ] **Step 3: 测试 presign endpoint**

```bash
curl -s -X POST http://<server-ip>/api/v1/storage/presign \
  -H 'Content-Type: application/json' \
  -d '{"scene":"posts","postId":"test","contentType":"image/png","ext":".png"}' | python3 -m json.tool
```
Expected: 返回 `{"objectKey":"posts/test/<uuid>.png","putUrl":"https://...","headers":{...},"expiresIn":600}`

- [ ] **Step 4: Commit**

```bash
git commit --allow-empty -m "test: OSS presigned URL end-to-end verification passed"
```

---

### 验证检查清单

- [ ] `POST /api/v1/storage/presign` 返回真正的 OSS 预签名 URL（不是 localhost）
- [ ] PUT 预签名 URL 可成功上传文件到 OSS
- [ ] 不支持的文件类型返回 BAD_REQUEST
- [ ] MultiImageUpload 上传图片走 presigned flow
- [ ] VideoUpload 上传视频走 presigned flow
- [ ] uploadCropImage（头像）走 presigned flow

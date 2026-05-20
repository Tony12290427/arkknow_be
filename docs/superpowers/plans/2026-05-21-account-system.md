# 账号系统架构 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现主账号(用户名+密码) + 多渠道绑定(手机/邮箱/Google)的统一账号系统。

**Architecture:** user_channels 表独立管理绑定关系，uk_channel 确保互斥。AuthService 新增 channelLogin/bindChannel/unbindChannel。前端 AuthModal 重构为三 Tab 布局。

**Tech Stack:** MyBatis, Spring Security, JWT, React 19

---

## 文件结构

```
zhizhou_be/
├── db/
│   └── schema.sql                                    # MODIFY — user_channels表 + username
├── src/main/java/com/arknow/
│   ├── auth/
│   │   ├── api/AuthController.java                   # MODIFY — 新增端点
│   │   ├── api/dto/
│   │   │   ├── ChannelLoginRequest.java              # NEW
│   │   │   ├── BindChannelRequest.java               # NEW
│   │   │   └── SetUsernameRequest.java               # NEW
│   │   ├── service/AuthService.java                  # MODIFY — channel逻辑
│   │   └── model/
│   │       └── UserChannel.java                      # NEW
│   └── user/
│       ├── domain/User.java                          # MODIFY — username字段
│       ├── mapper/UserMapper.java                    # MODIFY
│       └── mapper/UserChannelMapper.java             # NEW
└── src/main/resources/mapper/
    ├── UserMapper.xml                               # MODIFY
    └── UserChannelMapper.xml                        # NEW

zhizhou_react/
└── src/
    ├── components/modals/AuthModal.tsx               # MODIFY — 三Tab重构
    └── pages/OAuthCallback.tsx                       # NEW — OAuth回调
```

---

### Task 1: 数据库 — user_channels 表 + username 字段

- [ ] **Step 1: schema.sql 追加**

```sql
ALTER TABLE users ADD COLUMN username VARCHAR(64) UNIQUE AFTER id;

CREATE TABLE user_channels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    channel_type ENUM('PHONE','EMAIL','GOOGLE') NOT NULL,
    channel_value VARCHAR(256) NOT NULL,
    verified_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE KEY uk_channel (channel_type, channel_value),
    INDEX idx_user (user_id)
);
```

- [ ] **Step 2: User.java 加 username 字段**

```java
private String username;
```

- [ ] **Step 3: UserChannel.java 模型**

```java
package com.arknow.auth.model;
import java.time.Instant;
public class UserChannel {
    private Long id; private Long userId;
    private String channelType; private String channelValue;
    private Instant verifiedAt; private Instant createdAt;
    // getters/setters
}
```

- [ ] **Step 4: Commit**

---

### Task 2: 后端 — UserChannelMapper + AuthService 渠道逻辑

- [ ] **Step 1: UserChannelMapper.java**

```java
public interface UserChannelMapper {
    UserChannel findByTypeAndValue(@Param("type") String type, @Param("value") String value);
    void insert(UserChannel channel);
    List<UserChannel> findByUserId(@Param("userId") Long userId);
    void delete(@Param("id") Long id);
    boolean existsByTypeAndValue(@Param("type") String type, @Param("value") String value);
}
```

- [ ] **Step 2: UserChannelMapper.xml**

```xml
<select id="findByTypeAndValue" resultMap="channelMap">
    SELECT * FROM user_channels WHERE channel_type=#{type} AND channel_value=#{value}
</select>
<insert id="insert" useGeneratedKeys="true" keyProperty="id">
    INSERT INTO user_channels (user_id,channel_type,channel_value) VALUES (#{userId},#{channelType},#{channelValue})
</insert>
```

- [ ] **Step 3: AuthService — channelLogin**

```java
public TokenPair channelLogin(String type, String value, String username, String password) {
    UserChannel channel = userChannelMapper.findByTypeAndValue(type, value);
    if (channel == null) {
        // 场景2: 全新用户，必须提供username+password
        if (username == null || password == null) return null; // 返回null表示需要设置账号
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(username);
        user.setRole("USER");
        userMapper.insert(user);
        channel = new UserChannel();
        channel.setUserId(user.getId());
        channel.setChannelType(type);
        channel.setChannelValue(value);
        channel.setVerifiedAt(Instant.now());
        userChannelMapper.insert(channel);
        return jwtService.issueTokens(user);
    }
    // 场景3: 已绑定，直接登录
    User user = userMapper.findById(channel.getUserId());
    return jwtService.issueTokens(user);
}
```

- [ ] **Step 4: Commit**

---

### Task 3: 后端 — AuthController 新增端点

- [ ] **Step 1: 添加端点**

```java
@PostMapping("/login/channel")
public TokenResponse channelLogin(@RequestBody ChannelLoginRequest request) { ... }

@PostMapping("/bind-channel")
public Map<String,Boolean> bindChannel(@RequestBody BindChannelRequest req, @AuthenticationPrincipal Jwt jwt) { ... }

@DeleteMapping("/channels/{id}")
public Map<String,Boolean> unbindChannel(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) { ... }

@GetMapping("/channels")
public List<UserChannel> listChannels(@AuthenticationPrincipal Jwt jwt) { ... }
```

- [ ] **Step 2: 编译验证 + Commit**

---

### Task 4: 前端 — AuthModal 三 Tab 重构

- [ ] **Step 1: 重构为 Tab 布局**

顶部分割：账号密码登录（默认）/ 快捷登录（手机/邮箱/Google）

```
Tab 1 (默认): 用户名+密码 → POST /auth/login
Tab 2: 手机验证码 → POST /auth/login/channel {type:PHONE, value:...}
Tab 3: 邮箱验证码 → POST /auth/login/channel {type:EMAIL, value:...}
Tab 4: Google → window.location.href='/oauth2/authorization/google'
```

首次第三方登录返回 `requiresRegistration=true` → 弹用户名+密码设置窗口 → 再次提交 channelLogin 带 username+password。

- [ ] **Step 2: 构建验证 + Commit**

---

### Task 5: 前端 — OAuthCallback 页面 + 部署

- [ ] **Step 1: OAuthCallback.tsx**

```tsx
// 页面: /oauth/callback
// 后端 OAuth 成功后在 URL hash 带 token
// 解析 → 存 localStorage → 跳首页
useEffect(() => {
  const params = new URLSearchParams(window.location.hash.substring(1));
  const accessToken = params.get('accessToken');
  const refreshToken = params.get('refreshToken');
  if (accessToken) {
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
    window.location.href = '/';
  }
}, []);
```

- [ ] **Step 2: 部署到服务器**

- [ ] **Step 3: Commit**

---

### 验证检查清单

- [ ] 用户名+密码注册 → JWT
- [ ] Google 登录 → 首次设置用户名 → 自动绑定
- [ ] 已绑定 Google → 直接登录
- [ ] 登录后绑定手机号 → 成功
- [ ] 同一手机号绑定两个账号 → 报错
- [ ] 解绑后该渠道登录 → requiresRegistration

# 账号系统架构 — 设计规格

## 目标

主账号（用户名+密码）为核心，手机号/邮箱/Google 为可绑定第三方渠道，任选渠道登录进入同一账号。

## 数据库模型

新增 `user_channels` 表：

```sql
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

`users` 表新增 `username` VARCHAR(64) UNIQUE。

## API 端点

| Method | Path | Auth | 说明 |
|--------|------|------|------|
| POST | `/auth/register` | No | 用户名+密码注册 |
| POST | `/auth/login` | No | 用户名+密码登录 |
| POST | `/auth/login/channel` | No | 第三方渠道登录 |
| POST | `/auth/bind-channel` | Yes | 绑定新渠道到当前账号 |
| DELETE | `/auth/channels/{id}` | Yes | 解绑渠道 |
| GET | `/auth/channels` | Yes | 查询已绑定渠道 |

## 5 个场景

### 场景1: 纯账号密码注册
POST /auth/register → 校验username唯一性 → 创建users → 返回JWT

### 场景2: 第三方全新登录
POST /auth/login/channel → 查user_channels无记录 → 要求设置username+password → 创建users+user_channels → 返回JWT

### 场景3: 第三方已绑定登录
POST /auth/login/channel → 查user_channels有记录 → 找到user_id → 签发JWT

### 场景4: 绑定已有账号
POST /auth/bind-channel → 登录态校验 → 查channel未被占用 → INSERT user_channels

### 场景5: 解绑
DELETE /auth/channels/{id} → 登录态校验 → 删除记录（不能解绑最后一个渠道）

## 前端 UI

登录页：顶部账号密码登录（主入口），下方快捷登录（手机/邮箱/Google）
注册页：账号密码注册 + 快捷注册（自动创建账号+绑定渠道）
第三方首次登录：弹窗设置用户名+密码

## 验证

- [ ] 用户名+密码注册登录
- [ ] Google 首次登录→设置用户名→自动绑定
- [ ] 已绑 Google 直接登录
- [ ] 已登录用户绑手机号
- [ ] 解绑渠道后该渠道无法登录
- [ ] 一个手机号不能绑两个账号

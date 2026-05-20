# 账号注册系统 — 设计规格

## 目标

实现手机号注册（短信验证码）+ 邮箱注册（邮件验证码）+ Google OAuth 一键登录。

## 当前状态

- 注册/登录逻辑完整（验证码校验、BCrypt、JWT）
- 验证码仅打印到控制台（`LoggingCodeSender`），用户收不到
- Spring Boot Mail 已在 pom.xml

## 目标架构

```
登录方式:
  手机号 + 短信验证码
  邮箱 + 邮件验证码
  Google OAuth 一键登录

注册方式:
  手机号 + 短信验证码 → 注册
  邮箱 + 邮件验证码 → 注册
  Google OAuth → 首次自动注册

验证码发送:
  SmsCodeSender  → 阿里云 SMS SDK
  EmailCodeSender → Spring Boot Mail (SMTP)
```

## 后端改动

| 组件 | 改动 |
|------|------|
| `CodeSender.java` | 接口拆分为 SmsSender + EmailSender |
| `EmailCodeSender.java` | NEW — Spring Mail HTML 邮件 |
| `SmsCodeSender.java` | NEW — 阿里云 SMS SDK |
| `application.yml` | SMTP + 阿里云 SMS 配置 |
| `SecurityConfig.java` | `oauth2Login()` Google |
| `AuthService.java` | OAuth 回调自动注册/登录 |
| `User.java` | `googleId` 字段 |
| `db/schema.sql` | `google_id` 列 |

## Google OAuth 流程

```
用户点 "Google 登录" → 重定向 Google 授权
  → 回调 /login/oauth2/code/google
  → 查 google_id 是否存在
    → 存在: 生成 JWT
    → 不存在: INSERT (google_id + email + nickname)，生成 JWT
```

## 前端改动

- 注册/登录页 3 tab（手机号 / 邮箱 / Google）
- Google 登录按钮
- OAuth 回调处理 → 存 JWT → 跳首页

## 配置项

```yaml
# SMTP
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}

# Alibaba Cloud SMS
sms.access-key-id=${SMS_ACCESS_KEY_ID}
sms.access-key-secret=${SMS_ACCESS_KEY_SECRET}
sms.sign-name=知舟
sms.template-code=SMS_123456789

# Google OAuth
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
```

## 验证清单

- [ ] 手机号注册 → 短信验证码 → 成功
- [ ] 邮箱注册 → 邮件验证码 → 成功
- [ ] Google 登录 → 首次自动注册 → JWT 签发
- [ ] Google 登录 → 已有账号 → JWT 签发
- [ ] 验证码发送频率限制生效
- [ ] 验证码错误次数限制生效

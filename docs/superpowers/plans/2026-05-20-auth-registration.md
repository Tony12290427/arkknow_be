# 账号注册系统 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现手机号短信验证码注册、邮箱邮件验证码注册、Google OAuth 一键登录。

**Architecture:** CodeSender 接口拆为 EmailCodeSender + SmsCodeSender，VerificationService 按 identifierType 路由到对应发送器。SecurityConfig 加 oauth2Login()。AuthService 加 OAuth 用户处理。

**Tech Stack:** Spring Boot Mail, Alibaba Cloud SMS SDK, Spring Security OAuth2 Client

---

## 文件结构

```
zhizhou_be/
├── src/main/java/com/arknow/auth/verification/
│   ├── EmailCodeSender.java           # NEW — Spring Mail HTML 邮件
│   └── SmsCodeSender.java             # NEW — 阿里云 SMS SDK
├── src/main/java/com/arknow/auth/
│   ├── service/AuthService.java       # MODIFY — OAuth 自动注册/登录
│   └── config/SmsProperties.java      # NEW — 阿里云 SMS 配置
├── src/main/java/com/arknow/config/
│   └── SecurityConfig.java            # MODIFY — oauth2Login() Google
├── src/main/java/com/arknow/user/domain/
│   └── User.java                      # MODIFY — 加 googleId 字段
├── src/main/resources/
│   ├── application.yml                # MODIFY — SMTP + SMS + OAuth 配置
│   └── mapper/UserMapper.xml         # MODIFY — 加 google_id 查询
└── db/
    └── schema.sql                     # MODIFY — 加 google_id 列

zhizhou_react/
└── src/pages/                         # MODIFY — 登录/注册 UI（3 tab）
```

---

### Task 1: 数据库 — 加 google_id 列 + User 模型更新

- [ ] **Step 1: 数据库迁移**

```sql
ALTER TABLE users ADD COLUMN google_id VARCHAR(128) NULL UNIQUE AFTER email;
```

- [ ] **Step 2: User.java 加字段**

```java
private String googleId;
```

- [ ] **Step 3: UserMapper.xml 加查询方法**

```xml
<select id="findByGoogleId" resultMap="userMap">
    SELECT * FROM users WHERE google_id = #{googleId} AND deleted_at IS NULL
</select>
```

- [ ] **Step 4: 编译验证**

```bash
cd /Users/chuntingli/zhizhou_be && mvn compile -q
```

- [ ] **Step 5: Commit**

---

### Task 2: EmailCodeSender — Spring Mail 发送邮件验证码

- [ ] **Step 1: 创建 EmailCodeSender**

```java
package com.arknow.auth.verification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.mail.host")
public class EmailCodeSender implements CodeSender {
    private static final Logger log = LoggerFactory.getLogger(EmailCodeSender.class);
    private final JavaMailSender mailSender;

    public EmailCodeSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendCode(VerificationScene scene, String identifier, String code, int expireMinutes) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setTo(identifier);
            helper.setSubject("知舟 - 验证码");
            helper.setText(String.format("""
                <div style="font-family:Arial,sans-serif;max-width:400px;margin:0 auto">
                  <h2 style="color:#1a1a1a">知舟 验证码</h2>
                  <p>您的验证码是：</p>
                  <div style="font-size:32px;font-weight:bold;color:#2563eb;padding:12px 0">%s</div>
                  <p>%d 分钟内有效，请勿泄露。</p>
                </div>
                """, code, expireMinutes), true);
            mailSender.send(msg);
            log.info("Email code sent to {}", identifier);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", identifier, e.getMessage());
        }
    }
}
```

- [ ] **Step 2: application.yml 加 SMTP 配置**

```yaml
spring:
  mail:
    host: ${MAIL_HOST:smtp.gmail.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
```

- [ ] **Step 3: 编译 + Commit**

---

### Task 3: SmsCodeSender — 阿里云 SMS 发送短信验证码

- [ ] **Step 1: 创建 SmsProperties**

```java
package com.arknow.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sms")
public class SmsProperties {
    private String accessKeyId;
    private String accessKeySecret;
    private String signName = "知舟";
    private String templateCode;
}
```

- [ ] **Step 2: 创建 SmsCodeSender**

```java
package com.arknow.auth.verification;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.teaopenapi.models.Config;
import com.arknow.auth.config.SmsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sms.access-key-id")
public class SmsCodeSender implements CodeSender {
    private static final Logger log = LoggerFactory.getLogger(SmsCodeSender.class);
    private final SmsProperties props;
    private Client client;

    public SmsCodeSender(SmsProperties props) throws Exception {
        this.props = props;
        Config config = new Config().setAccessKeyId(props.getAccessKeyId())
                .setAccessKeySecret(props.getAccessKeySecret());
        config.endpoint = "dysmsapi.aliyuncs.com";
        this.client = new Client(config);
    }

    @Override
    public void sendCode(VerificationScene scene, String identifier, String code, int expireMinutes) {
        try {
            SendSmsRequest req = new SendSmsRequest()
                    .setPhoneNumbers(identifier)
                    .setSignName(props.getSignName())
                    .setTemplateCode(props.getTemplateCode())
                    .setTemplateParam("{\"code\":\"" + code + "\"}");
            client.sendSms(req);
            log.info("SMS sent to {}", identifier);
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", identifier, e.getMessage());
        }
    }
}
```

- [ ] **Step 3: application.yml 加 SMS 配置**

```yaml
sms:
  access-key-id: ${SMS_ACCESS_KEY_ID:}
  access-key-secret: ${SMS_ACCESS_KEY_SECRET:}
  sign-name: 知舟
  template-code: ${SMS_TEMPLATE_CODE:}
```

- [ ] **Step 4: pom.xml 加 SMS SDK 依赖**

```xml
<dependency>
    <groupId>com.aliyun</groupId>
    <artifactId>dysmsapi20170525</artifactId>
    <version>3.0.1</version>
</dependency>
```

- [ ] **Step 5: 编译 + Commit**

---

### Task 4: Google OAuth 登录

- [ ] **Step 1: SecurityConfig 加 oauth2Login**

```java
http.oauth2Login(oauth2 -> oauth2
    .successHandler((request, response, authentication) -> {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        TokenPair tokens = authService.handleOAuth2Login(oauth2User);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
            "accessToken", tokens.accessToken(),
            "refreshToken", tokens.refreshToken()
        )));
    })
);
```

- [ ] **Step 2: AuthService.handleOAuth2Login**

```java
public TokenPair handleOAuth2Login(OAuth2User oauth2User) {
    String googleId = oauth2User.getAttribute("sub");
    String email = oauth2User.getAttribute("email");
    String name = oauth2User.getAttribute("name");

    User user = userMapper.findByGoogleId(googleId);
    if (user == null) {
        user = new User();
        user.setGoogleId(googleId);
        user.setEmail(email);
        user.setNickname(name != null ? name : "知舟用户" + UUID.randomUUID().toString().substring(0, 8));
        user.setRole("USER");
        userMapper.insert(user);
    }
    return jwtService.issueTokens(user);
}
```

- [ ] **Step 3: application.yml 加 OAuth 配置**

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid,profile,email
```

- [ ] **Step 4: 编译 + Commit**

---

### Task 5: 前端 — 登录/注册 UI 更新

- [ ] **Step 1: 加 Google 登录按钮**

在 AuthModal.tsx 加 "继续使用 Google 登录" 按钮，点击跳转 `/oauth2/authorization/google`

- [ ] **Step 2: OAuth 回调处理**

前端检测 URL 参数中的 token → 存 localStorage → 跳转首页

- [ ] **Step 3: 构建验证 + Commit**

---

### Task 6: 端到端验证

- [ ] **Step 1: 测试邮箱注册流程**

```bash
curl -s -X POST http://8.218.64.22/api/v1/auth/send-code \
  -H 'Content-Type: application/json' \
  -d '{"identifierType":"EMAIL","identifier":"your@email.com"}'
# 预期: 收到验证码邮件
```

- [ ] **Step 2: Google OAuth 验证**

浏览器访问 http://8.218.64.22/oauth2/authorization/google → Google 授权 → 回调成功

- [ ] **Step 3: Commit**

---

### 验证检查清单

- [ ] 邮箱验证码真实发送到收件箱
- [ ] 短信验证码真实发送到手机
- [ ] Google OAuth 回调成功，JWT 签发
- [ ] 首次 Google 登录自动注册用户
- [ ] 已有 Google 用户登录不重复创建
- [ ] 验证码频率限制仍然生效
- [ ] 错误验证码次数限制仍然生效

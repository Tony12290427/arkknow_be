@auth @critical
Feature: 账号注册与登录

  支持手机号注册（短信验证码）、邮箱注册（邮件验证码）、Google OAuth 登录。
  核心目标：多渠道注册，验证码安全防刷，OAuth 自动注册。

  Background:
    Given 后端服务运行正常

  # ============================================================
  # 手机号注册
  # ============================================================

  @smoke @happy-path
  Scenario: 手机号注册 — 发送验证码
    When POST /api/v1/auth/send-code 请求体:
      """
      {"identifierType":"PHONE","identifier":"13800138000"}
      """
    Then 响应状态码为 200
    And 响应包含 "code sent"

  Scenario: 手机号注册 — 正确验证码完成注册
    Given 已向 13800138000 发送验证码
    When POST /api/v1/auth/register 请求体:
      """
      {"identifierType":"PHONE","identifier":"13800138000","code":"正确验证码","agreeTerms":true}
      """
    Then 响应状态码为 200
    And 响应包含 accessToken 和 refreshToken

  # ============================================================
  # 邮箱注册
  # ============================================================

  Scenario: 邮箱注册 — 发送验证码
    When POST /api/v1/auth/send-code 请求体:
      """
      {"identifierType":"EMAIL","identifier":"newuser@example.com"}
      """
    Then 响应状态码为 200

  Scenario: 邮箱注册 — 正确验证码完成注册
    Given 已向 newuser@example.com 发送验证码
    When POST /api/v1/auth/register 请求体:
      """
      {"identifierType":"EMAIL","identifier":"newuser@example.com","code":"正确验证码","agreeTerms":true}
      """
    Then 响应状态码为 200
    And 响应包含 accessToken 和 refreshToken

  # ============================================================
  # 验证码安全
  # ============================================================

  Scenario: 错误验证码被拒绝
    Given 已向 13800138001 发送验证码 "123456"
    When POST /api/v1/auth/register 请求体:
      """
      {"identifierType":"PHONE","identifier":"13800138001","code":"000000","agreeTerms":true}
      """
    Then 响应状态码为 400
    And 响应包含 "验证码错误"

  Scenario: 超次数尝试被锁定
    Given 已向 13800138002 发送验证码
    And 已连续输入 5 次错误验证码
    When POST /api/v1/auth/register 请求体:
      """
      {"identifierType":"PHONE","identifier":"13800138002","code":"123456","agreeTerms":true}
      """
    Then 响应状态码为 400
    And 响应包含 "too many attempts"

  Scenario: 60 秒内重复发送被拒绝
    Given 刚向 13800138003 发送了验证码
    When POST /api/v1/auth/send-code 请求体:
      """
      {"identifierType":"PHONE","identifier":"13800138003"}
      """
    Then 响应状态码为 429
    And 响应包含 "请稍后再试"

  # ============================================================
  # Google OAuth 登录
  # ============================================================

  Scenario: Google 登录 — 首次自动注册
    Given Google OAuth 返回有效 ID Token:
      """
      {"email":"googleuser@gmail.com","sub":"google-12345","name":"Google User"}
      """
    When 访问 /api/v1/auth/oauth2/google 回调
    Then 响应状态码为 200
    And 响应包含 accessToken 和 refreshToken
    And 数据库中新增用户 google_id 为 "google-12345"

  Scenario: Google 登录 — 已有账号
    Given 用户 google_id "google-12345" 已存在
    When 访问 /api/v1/auth/oauth2/google 回调
    Then 响应状态码为 200
    And 响应包含 accessToken 和 refreshToken
    And 数据库用户数未增加

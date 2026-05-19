@storage @critical
Feature: OSS 预签名 URL 直传

  客户端通过 /api/v1/storage/presign 获取预签名 PUT URL，直传文件到 OSS。
  核心目标：减少应用服务器带宽、支持大文件上传、类型和大小校验。

  Background:
    Given 后端服务运行正常

  @smoke @happy-path
  Scenario: 请求图片上传的预签名 URL
    When 客户端 POST /api/v1/storage/presign 请求体:
      """
      {"scene":"posts","postId":"test","contentType":"image/png","ext":".png","size":1024}
      """
    Then 响应状态码为 200
    And 响应包含 putUrl 字段
    And putUrl 是有效的 HTTPS URL
    And 响应包含 objectKey 字段
    And expiresIn 为 600

  @validation
  Scenario: 不支持的文件类型被拒绝
    When 客户端 POST /api/v1/storage/presign 请求体:
      """
      {"scene":"posts","postId":"test","contentType":"text/html","ext":".html","size":1024}
      """
    Then 响应状态码为 400
    And 响应包含错误信息

  @validation
  Scenario: 空 Content-Type 被拒绝
    When 客户端 POST /api/v1/storage/presign 请求体:
      """
      {"scene":"posts","postId":"test","contentType":"","ext":".png","size":1024}
      """
    Then 响应状态码为 400

  @validation
  Scenario: 超大文件被拒绝
    When 客户端 POST /api/v1/storage/presign 请求体:
      """
      {"scene":"posts","postId":"test","contentType":"image/png","ext":".png","size":20971520}
      """
    Then 响应状态码为 400
    And 响应包含 "File too large" 错误

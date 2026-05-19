# 评论软删除层级规则 — 设计文档

## 规则总览

| 场景 | 行为 |
|------|------|
| 删自己的一级评论（有子回复） | 软删自己，子回复完整保留可见 |
| 删自己的一级评论（无子回复） | 软删自己 |
| 删自己的二级回复 | 软删自己 |
| 删别人的评论 | 拒绝（permission: user_id == uid） |
| 删帖子 | 帖子已走软删/物理删，所有关联评论统一软删 |

## DB 变更

```sql
ALTER TABLE comments ADD COLUMN deleted_at DATETIME NULL;
```

## 后端变更

### CommentMapper

新增两个方法：

```java
// 替换 deleteById
int softDeleteById(@Param("id") long id, @Param("userId") long userId);

// 帖子级联：软删该帖所有评论
int softDeleteByPostId(@Param("postId") long postId);
```

CommentMapper.xml:

```xml
<update id="softDeleteById">
    UPDATE comments SET deleted_at = NOW()
    WHERE id = #{id} AND user_id = #{userId} AND deleted_at IS NULL
</update>

<update id="softDeleteByPostId">
    UPDATE comments SET deleted_at = NOW()
    WHERE post_id = #{postId} AND deleted_at IS NULL
</update>
```

### Comment 模型

新增字段：

```java
private Instant deletedAt;
// getter/setter
```

### CommentController.delete

- 去掉 `countReplies > 0` 阻止逻辑
- 改为调用 `softDeleteById`

### CommentController.list / replies

- 查询 SQL 需要 JOIN 时返回 `deleted_at`
- Controller 正常返回所有评论（包括已删除的），前端自己决定渲染

### KnowPostServiceImpl.delete

- 新增调用 `commentMapper.softDeleteByPostId(postId)`

## 前端变更

### 评论 API

- `commentApi.deleteComment(id)` 无变化（仍调 DELETE /comments/{id}）

### DetailCard 渲染

每条评论新增 `deleted` 字段判断：

**已删除评论**：
- 保留头像 + 昵称
- content 替换为灰色 `"原评论已删除"`
- 隐藏点赞、回复、删除、举报按钮
- 子回复正常完整展示

**正常评论**：现有逻辑不变

## 测试验证

| 测试场景 | 预期结果 |
|----------|----------|
| 删无回复的一级评论 | 评论变成"原评论已删除" |
| 删有回复的一级评论 | 父评论占位，子回复完整可见 |
| 删二级回复 | 该回复变成占位 |
| 删别人的评论 | 403/401 |
| 删帖子 | 所有评论统一变占位 |
| 已删除评论的子回复 | 正常展示，可继续回复 |

package com.arknow.comment.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.Instant;

public class Comment {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long postId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    // joined fields
    private String userNickname;
    private String userAvatar;
    private Integer replyCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPostId() { return postId; }
    public void setPostId(Long v) { this.postId = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long v) { this.parentId = v; }
    public String getContent() { return content; }
    public void setContent(String v) { this.content = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant v) { this.deletedAt = v; }
    public String getUserNickname() { return userNickname; }
    public void setUserNickname(String v) { this.userNickname = v; }
    public String getUserAvatar() { return userAvatar; }
    public void setUserAvatar(String v) { this.userAvatar = v; }
    public Integer getReplyCount() { return replyCount; }
    public void setReplyCount(Integer v) { this.replyCount = v; }
}

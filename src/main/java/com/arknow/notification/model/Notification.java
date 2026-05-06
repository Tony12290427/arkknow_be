package com.arknow.notification.model;

import java.time.Instant;

public class Notification {
    private Long id;
    private Long userId;
    private String type; // like, fav, follow, comment
    private Long actorId;
    private Long postId;
    private Long commentId;
    private Boolean isRead;
    private Instant createdAt;
    // joined
    private String actorNickname;
    private String actorAvatar;
    private String postTitle;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long v) { this.actorId = v; }
    public Long getPostId() { return postId; }
    public void setPostId(Long v) { this.postId = v; }
    public Long getCommentId() { return commentId; }
    public void setCommentId(Long v) { this.commentId = v; }
    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean v) { this.isRead = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public String getActorNickname() { return actorNickname; }
    public void setActorNickname(String v) { this.actorNickname = v; }
    public String getActorAvatar() { return actorAvatar; }
    public void setActorAvatar(String v) { this.actorAvatar = v; }
    public String getPostTitle() { return postTitle; }
    public void setPostTitle(String v) { this.postTitle = v; }
}

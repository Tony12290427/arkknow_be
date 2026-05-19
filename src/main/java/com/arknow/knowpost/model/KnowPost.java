package com.arknow.knowpost.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.Instant;

public class KnowPost {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private Long tagId;
    private String tags;
    private String title;
    private String description;
    private String contentUrl;
    private String contentObjectKey;
    private String contentEtag;
    private Long contentSize;
    private String contentSha256;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long creatorId;
    private Boolean isTop;
    private String type;
    private String visible;
    private String imgUrls;
    private String videoUrl;
    private String status;
    private Instant createTime;
    private Instant updateTime;
    private Instant publishTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getContentUrl() { return contentUrl; }
    public void setContentUrl(String contentUrl) { this.contentUrl = contentUrl; }
    public String getContentObjectKey() { return contentObjectKey; }
    public void setContentObjectKey(String contentObjectKey) { this.contentObjectKey = contentObjectKey; }
    public String getContentEtag() { return contentEtag; }
    public void setContentEtag(String contentEtag) { this.contentEtag = contentEtag; }
    public Long getContentSize() { return contentSize; }
    public void setContentSize(Long contentSize) { this.contentSize = contentSize; }
    public String getContentSha256() { return contentSha256; }
    public void setContentSha256(String contentSha256) { this.contentSha256 = contentSha256; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public Boolean getIsTop() { return isTop; }
    public void setIsTop(Boolean isTop) { this.isTop = isTop; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getVisible() { return visible; }
    public void setVisible(String visible) { this.visible = visible; }
    public String getImgUrls() { return imgUrls; }
    public void setImgUrls(String imgUrls) { this.imgUrls = imgUrls; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreateTime() { return createTime; }
    public void setCreateTime(Instant createTime) { this.createTime = createTime; }
    public Instant getUpdateTime() { return updateTime; }
    public void setUpdateTime(Instant updateTime) { this.updateTime = updateTime; }
    public Instant getPublishTime() { return publishTime; }
    public void setPublishTime(Instant publishTime) { this.publishTime = publishTime; }
}

package com.arknow.knowpost.model;

public class KnowPostFeedRow {
    private String id;
    private String title;
    private String description;
    private String imgUrls;
    private String tags;
    private String authorAvatar;
    private String authorNickname;
    private String authorTagJson;
    private Long likeCount;
    private Long favoriteCount;
    private Boolean isTop;
    private String visible;
    private String type;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImgUrls() { return imgUrls; }
    public void setImgUrls(String imgUrls) { this.imgUrls = imgUrls; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getAuthorAvatar() { return authorAvatar; }
    public void setAuthorAvatar(String authorAvatar) { this.authorAvatar = authorAvatar; }
    public String getAuthorNickname() { return authorNickname; }
    public void setAuthorNickname(String authorNickname) { this.authorNickname = authorNickname; }
    public String getAuthorTagJson() { return authorTagJson; }
    public void setAuthorTagJson(String authorTagJson) { this.authorTagJson = authorTagJson; }
    public Long getLikeCount() { return likeCount; }
    public void setLikeCount(Long likeCount) { this.likeCount = likeCount; }
    public Long getFavoriteCount() { return favoriteCount; }
    public void setFavoriteCount(Long favoriteCount) { this.favoriteCount = favoriteCount; }
    public Boolean getIsTop() { return isTop; }
    public void setIsTop(Boolean isTop) { this.isTop = isTop; }
    public String getVisible() { return visible; }
    public void setVisible(String visible) { this.visible = visible; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}

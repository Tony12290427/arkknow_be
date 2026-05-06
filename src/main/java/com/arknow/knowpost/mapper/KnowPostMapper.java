package com.arknow.knowpost.mapper;

import com.arknow.knowpost.model.KnowPost;
import com.arknow.knowpost.model.KnowPostDetailRow;
import com.arknow.knowpost.model.KnowPostFeedRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface KnowPostMapper {
    void insert(KnowPost post);
    int update(KnowPost post);
    int softDelete(@Param("id") long id, @Param("creatorId") long creatorId);
    Optional<KnowPost> findById(@Param("id") long id);
    KnowPostDetailRow findDetailById(@Param("id") long id);
    List<KnowPostFeedRow> listFeedPublic(@Param("limit") int limit, @Param("offset") int offset);
    List<KnowPostFeedRow> listFeedByCreator(@Param("creatorId") long creatorId, @Param("limit") int limit, @Param("offset") int offset);
    int updateTop(@Param("id") long id, @Param("isTop") boolean isTop);
    int updateVisibility(@Param("id") long id, @Param("visible") String visible);
    int publish(@Param("id") long id, @Param("publishTime") java.time.Instant publishTime);
    int updateContentConfirm(@Param("id") long id, @Param("objectKey") String objectKey,
                             @Param("etag") String etag, @Param("size") long size,
                             @Param("sha256") String sha256, @Param("contentUrl") String contentUrl);
    List<Long> listMyPublishedIds(@Param("creatorId") long creatorId);
    List<KnowPostFeedRow> listFeedByFollowing(@Param("userId") long userId, @Param("limit") int limit, @Param("offset") int offset);
}

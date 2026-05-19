package com.arknow.relation.mapper;

import com.arknow.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * MyBatis mapper for following and follower tables.
 * <p>
 * Design: following is the authoritative source-of-truth table.
 * Follower is a projection updated asynchronously via Outbox events.
 */
@Mapper
public interface RelationMapper {

    // ==================== Following ====================

    /** Inserts an active follow relationship. Uses snowflake ID. */
    int insertFollowing(@Param("id") long id, @Param("fromUserId") long fromUserId,
                         @Param("toUserId") long toUserId, @Param("relStatus") int relStatus);

    /** Cancels a follow (sets rel_status to 0). */
    int cancelFollowing(@Param("fromUserId") long fromUserId, @Param("toUserId") long toUserId);

    /** Checks if a follow relationship exists in either direction. */
    boolean existsFollowing(@Param("fromUserId") long fromUserId, @Param("toUserId") long toUserId);

    // ==================== Follower (async projection) ====================

    /** Inserts into the follower projection table. */
    int insertFollower(@Param("id") long id, @Param("toUserId") long toUserId,
                        @Param("fromUserId") long fromUserId, @Param("relStatus") int relStatus);

    /** Cancels a follower projection (sets rel_status to 0). */
    int cancelFollower(@Param("toUserId") long toUserId, @Param("fromUserId") long fromUserId);

    // ==================== Queries ====================

    /** Returns users followed by the given user (paginated). */
    List<User> listFollowing(@Param("userId") long userId, @Param("limit") int limit,
                              @Param("offset") int offset);

    /** Returns followers of the given user (paginated). */
    List<User> listFollowers(@Param("userId") long userId, @Param("limit") int limit,
                              @Param("offset") int offset);

    /** Count of active follow relationships. */
    long countFollowingActive(@Param("userId") long userId);

    /** Count of active follower relationships. */
    long countFollowerActive(@Param("userId") long userId);

    // ==================== Admin ====================

    /** List all follow relationships (admin). */
    List<Map<String, Object>> listAllFollowing(@Param("offset") int offset, @Param("limit") int limit);

    /** Count all active follow relationships. */
    long countAllFollowing();

    /** Delete a follow relationship by id. */
    int deleteFollowingById(@Param("id") long id);
}

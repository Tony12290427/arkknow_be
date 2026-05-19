package com.arknow.notification.mapper;

import com.arknow.notification.model.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationMapper {
    void insert(Notification n);
    List<Notification> listByUser(@Param("userId") long userId, @Param("offset") int offset, @Param("limit") int limit);
    int countUnread(@Param("userId") long userId);
    int markRead(@Param("userId") long userId);
    int markReadById(@Param("id") long id);
    int deleteById(@Param("id") long id, @Param("userId") long userId);

    // ==================== Admin ====================

    /** List all notifications (admin, all users). */
    List<Notification> listAll(@Param("offset") int offset, @Param("limit") int limit);

    /** Count all notifications. */
    long countAll();

    /** Delete notification by id (admin, any user). */
    int deleteByIdAdmin(@Param("id") long id);

    /** Insert a system notification. */
    int insertSystem(@Param("id") long id, @Param("userId") long userId,
                     @Param("type") String type, @Param("actorId") long actorId,
                     @Param("postId") Long postId, @Param("commentId") Long commentId);
}

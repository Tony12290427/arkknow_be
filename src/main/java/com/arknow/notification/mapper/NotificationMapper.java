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
}

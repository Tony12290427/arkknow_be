package com.arknow.user.mapper;

import com.arknow.auth.model.UserChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserChannelMapper {
    UserChannel findByTypeAndValue(@Param("type") String type, @Param("value") String value);
    UserChannel findById(@Param("id") Long id);
    void insert(UserChannel channel);
    List<UserChannel> findByUserId(@Param("userId") Long userId);
    void delete(@Param("id") Long id);
    boolean existsByTypeAndValue(@Param("type") String type, @Param("value") String value);
    int countByUserId(@Param("userId") Long userId);
}

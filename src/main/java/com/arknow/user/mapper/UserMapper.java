package com.arknow.user.mapper;

import com.arknow.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserMapper {
    boolean existsByPhone(@Param("phone") String phone);
    boolean existsByEmail(@Param("email") String email);
    Optional<User> findByPhone(@Param("phone") String phone);
    Optional<User> findByEmail(@Param("email") String email);
    Optional<User> findById(@Param("id") long id);
    void insert(User user);
    int updatePassword(User user);
    int updateProfile(User user);
    int updateAvatar(@Param("id") long id, @Param("avatar") String avatar);
}

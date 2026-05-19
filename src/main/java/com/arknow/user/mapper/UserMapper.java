package com.arknow.user.mapper;

import com.arknow.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
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
    int updateTags(@Param("userId") long userId, @Param("tagsJson") String tagsJson);
    int updateEmail(@Param("id") long id, @Param("email") String email);
    int softDelete(@Param("id") long id);

    @Select("SELECT * FROM users WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<User> listAll(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL")
    int countAll();

    @Select("SELECT * FROM users WHERE deleted_at IS NULL AND (phone LIKE #{keyword} OR nickname LIKE #{keyword} OR email LIKE #{keyword}) ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<User> searchUsers(@Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND (phone LIKE #{keyword} OR nickname LIKE #{keyword} OR email LIKE #{keyword})")
    int countSearchUsers(@Param("keyword") String keyword);

    @Update("UPDATE users SET role = #{role}, updated_at = NOW() WHERE id = #{id}")
    int updateRole(@Param("id") long id, @Param("role") String role);

    @Select("SELECT * FROM users WHERE role = #{role} AND deleted_at IS NULL ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<User> listByRole(@Param("role") String role, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM users WHERE role = #{role} AND deleted_at IS NULL")
    int countByRole(@Param("role") String role);
}

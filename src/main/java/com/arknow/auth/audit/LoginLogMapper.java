package com.arknow.auth.audit;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface LoginLogMapper {
    void insert(LoginLog log);

    // Admin methods
    List<LoginLog> listAll(@Param("offset") int offset, @Param("limit") int limit);
    int countAll();
    Optional<LoginLog> findById(@Param("id") long id);
}

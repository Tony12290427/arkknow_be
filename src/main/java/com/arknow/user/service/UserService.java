package com.arknow.user.service;

import com.arknow.user.domain.User;

import java.util.Optional;

public interface UserService {
    boolean existsByPhone(String phone);
    boolean existsByEmail(String email);
    Optional<User> findByPhone(String phone);
    Optional<User> findByEmail(String email);
    Optional<User> findById(long id);
    void createUser(User user);
    void updatePassword(User user);
}

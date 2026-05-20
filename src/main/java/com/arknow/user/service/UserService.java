package com.arknow.user.service;

import com.arknow.user.domain.User;

import java.util.Optional;

public interface UserService {
    boolean existsByPhone(String phone);
    boolean existsByEmail(String email);
    Optional<User> findByPhone(String phone);
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleId(String googleId);
    Optional<User> findById(long id);
    Optional<User> findByUsername(String username);
    void createUser(User user);
    void updatePassword(User user);
    void updateEmail(long userId, String email);
    void updatePhone(long userId, String phone);
    void softDelete(long userId);
}

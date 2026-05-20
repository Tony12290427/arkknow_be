package com.arknow.user.service.impl;

import com.arknow.common.exception.BusinessException;
import com.arknow.common.exception.ErrorCode;
import com.arknow.user.domain.User;
import com.arknow.user.mapper.UserMapper;
import com.arknow.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public boolean existsByPhone(String phone) {
        return userMapper.existsByPhone(phone);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userMapper.existsByEmail(email);
    }

    @Override
    public Optional<User> findByPhone(String phone) {
        return userMapper.findByPhone(phone);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userMapper.findByEmail(email);
    }

    @Override
    public Optional<User> findByGoogleId(String googleId) {
        return userMapper.findByGoogleId(googleId);
    }

    @Override
    public Optional<User> findById(long id) {
        return userMapper.findById(id);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    @Override
    public void createUser(User user) {
        userMapper.insert(user);
    }

    @Override
    public void updatePassword(User user) {
        int rows = userMapper.updatePassword(user);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND);
        }
    }

    @Override
    public void updateEmail(long userId, String email) {
        int rows = userMapper.updateEmail(userId, email);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND);
        }
    }

    @Override
    public void updatePhone(long userId, String phone) {
        int rows = userMapper.updatePhone(userId, phone);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND);
        }
    }

    @Override
    public void softDelete(long userId) {
        int rows = userMapper.softDelete(userId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND);
        }
    }
}

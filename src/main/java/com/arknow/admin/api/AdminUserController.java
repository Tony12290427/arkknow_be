package com.arknow.admin.api;

import com.arknow.common.exception.BusinessException;
import com.arknow.common.exception.ErrorCode;
import com.arknow.user.mapper.UserMapper;
import com.arknow.user.domain.User;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final UserMapper userMapper;

    public AdminUserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String keyword) {
        int offset = (page - 1) * size;
        List<User> users;
        int total;
        if (keyword != null && !keyword.isBlank()) {
            users = userMapper.searchUsers("%" + keyword + "%", offset, size);
            total = userMapper.countSearchUsers("%" + keyword + "%");
        } else {
            users = userMapper.listAll(offset, size);
            total = userMapper.countAll();
        }
        return Map.of("items", users, "total", total, "page", page, "size", size);
    }

    @GetMapping("/{id}")
    public User detail(@PathVariable long id) {
        return userMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND, "用户不存在"));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        User user = userMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND, "用户不存在"));
        if (body.containsKey("role")) {
            userMapper.updateRole(id, (String) body.get("role"));
        }
        return Map.of("success", true);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable long id) {
        int rows = userMapper.softDelete(id);
        return Map.of("success", rows > 0);
    }

    @PostMapping("/batch-delete")
    public Map<String, Object> batchDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("ids");
        int count = 0;
        for (int id : ids) {
            count += userMapper.softDelete(id);
        }
        return Map.of("success", true, "deleted", count);
    }
}

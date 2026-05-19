package com.arknow.admin.api;

import com.arknow.user.mapper.UserMapper;
import com.arknow.user.domain.User;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/admins")
public class AdminAdminController {

    private final UserMapper userMapper;

    public AdminAdminController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<User> admins = userMapper.listByRole("ADMIN", offset, size);
        int total = userMapper.countByRole("ADMIN");
        return Map.of("items", admins, "total", total, "page", page, "size", size);
    }

    @PostMapping
    public Map<String, Object> promote(@RequestBody Map<String, Object> body) {
        long userId = Long.parseLong(String.valueOf(body.get("userId")));
        userMapper.updateRole(userId, "ADMIN");
        return Map.of("success", true);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> demote(@PathVariable long id) {
        userMapper.updateRole(id, "USER");
        return Map.of("success", true);
    }
}

package com.arknow.admin.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/categories")
public class AdminCategoryController {
    private final JdbcTemplate jdbcTemplate;

    public AdminCategoryController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public Map<String, Object> list() {
        List<String> types = jdbcTemplate.queryForList(
                "SELECT DISTINCT type FROM know_posts WHERE status != 'deleted'",
                String.class);
        List<Map<String, String>> items = new ArrayList<>();
        for (String type : types) {
            if (type != null && !type.isBlank()) {
                items.add(Map.of("name", type));
            }
        }
        return Map.of("items", items, "total", items.size());
    }

    @PostMapping
    public Map<String, Object> create() {
        // Categories are derived from post types, not separate entities
        return Map.of("success", true);
    }

    @DeleteMapping
    public Map<String, Object> delete() {
        // Categories are derived from post types, not separate entities
        return Map.of("success", true);
    }
}

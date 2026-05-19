package com.arknow.admin.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/tags")
public class AdminTagController {
    private final JdbcTemplate jdbcTemplate;

    public AdminTagController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public Map<String, Object> list() {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT DISTINCT tags FROM know_posts WHERE status != 'deleted' AND tags IS NOT NULL",
                String.class);
        Set<String> tagSet = new LinkedHashSet<>();
        for (String tagsJson : rows) {
            if (tagsJson == null || tagsJson.isBlank()) continue;
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String[] parsed = mapper.readValue(tagsJson, String[].class);
                for (String tag : parsed) {
                    if (tag != null && !tag.isBlank()) {
                        tagSet.add(tag.trim());
                    }
                }
            } catch (Exception ignored) {
                // skip malformed JSON
            }
        }
        return Map.of("items", new ArrayList<>(tagSet), "total", tagSet.size());
    }

    @PostMapping
    public Map<String, Object> create() {
        // Tags are embedded in posts, not separate entities
        return Map.of("success", true);
    }

    @DeleteMapping
    public Map<String, Object> delete() {
        // Tags are embedded in posts, not separate entities
        return Map.of("success", true);
    }
}

package com.arknow.tag.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {
    private final JdbcTemplate jdbc;

    public TagController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int limit) {
        List<String> rows = jdbc.queryForList(
            "SELECT DISTINCT tags FROM know_posts WHERE status = 'published' AND tags IS NOT NULL AND tags != '[]'",
            String.class);
        Set<String> all = new LinkedHashSet<>();
        for (String r : rows) {
            try {
                String[] parts = new com.fasterxml.jackson.databind.ObjectMapper().readValue(r, String[].class);
                for (String p : parts) all.add(p.trim());
            } catch (Exception ignored) {}
        }
        List<String> list = new ArrayList<>(all);
        int offset = (page - 1) * limit;
        List<String> pageItems = offset < list.size() ? list.subList(offset, Math.min(offset + limit, list.size())) : List.of();
        return Map.of("items", pageItems, "total", list.size());
    }

    @GetMapping("/hot")
    public Map<String, Object> hot(@RequestParam(defaultValue = "10") int limit) {
        List<String> rows = jdbc.queryForList(
            "SELECT tags FROM know_posts WHERE status = 'published' AND tags IS NOT NULL AND tags != '[]' ORDER BY publish_time DESC LIMIT 500",
            String.class);
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (String r : rows) {
            try {
                String[] parts = new com.fasterxml.jackson.databind.ObjectMapper().readValue(r, String[].class);
                for (String p : parts) counter.merge(p.trim(), 1, Integer::sum);
            } catch (Exception ignored) {}
        }
        List<Map<String, Object>> items = counter.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(limit)
            .map(e -> Map.<String, Object>of("name", e.getKey(), "count", e.getValue()))
            .toList();
        return Map.of("items", items);
    }
}

package com.arknow.admin.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/collections")
public class AdminCollectionController {

    private final JdbcTemplate db;

    public AdminCollectionController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<Map<String, Object>> items = db.queryForList(
            "SELECT c.id, c.user_id, c.name, c.created_at, c.updated_at, " +
            "u.nickname AS user_nickname, " +
            "(SELECT COUNT(*) FROM collection_items ci WHERE ci.collection_id = c.id) AS item_count " +
            "FROM collections c LEFT JOIN users u ON c.user_id = u.id " +
            "ORDER BY c.created_at DESC LIMIT ? OFFSET ?", size, offset);
        Integer total = db.queryForObject("SELECT COUNT(*) FROM collections", Integer.class);
        return Map.of("items", items, "total", total != null ? total : 0, "page", page, "size", size);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        Map<String, Object> collection = db.queryForMap(
            "SELECT c.id, c.user_id, c.name, c.created_at, c.updated_at, " +
            "u.nickname AS user_nickname " +
            "FROM collections c LEFT JOIN users u ON c.user_id = u.id WHERE c.id = ?", id);
        List<Map<String, Object>> items = db.queryForList(
            "SELECT ci.id, ci.post_id, ci.created_at FROM collection_items ci WHERE ci.collection_id = ? ORDER BY ci.created_at DESC", id);
        collection.put("items", items);
        return collection;
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable long id) {
        db.update("DELETE FROM collection_items WHERE collection_id = ?", id);
        int rows = db.update("DELETE FROM collections WHERE id = ?", id);
        return Map.of("success", rows > 0);
    }

    @PostMapping("/batch-delete")
    public Map<String, Object> batchDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("ids");
        int count = 0;
        for (int id : ids) {
            db.update("DELETE FROM collection_items WHERE collection_id = ?", id);
            count += db.update("DELETE FROM collections WHERE id = ?", id);
        }
        return Map.of("success", true, "deleted", count);
    }
}

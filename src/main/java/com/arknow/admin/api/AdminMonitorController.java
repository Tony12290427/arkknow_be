package com.arknow.admin.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/monitor")
public class AdminMonitorController {

    private final JdbcTemplate db;

    public AdminMonitorController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping("/activities")
    public Map<String, Object> activities() {
        Map<String, Object> stats = new LinkedHashMap<>();

        Integer totalUsers = db.queryForObject(
            "SELECT COUNT(*) FROM users WHERE deleted_at IS NULL", Integer.class);
        stats.put("totalUsers", totalUsers != null ? totalUsers : 0);

        Integer totalPosts = db.queryForObject(
            "SELECT COUNT(*) FROM know_posts WHERE deleted_at IS NULL", Integer.class);
        stats.put("totalPosts", totalPosts != null ? totalPosts : 0);

        Integer totalComments = db.queryForObject(
            "SELECT COUNT(*) FROM comments WHERE deleted_at IS NULL", Integer.class);
        stats.put("totalComments", totalComments != null ? totalComments : 0);

        Integer newUsersToday = db.queryForObject(
            "SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND DATE(created_at) = CURDATE()", Integer.class);
        stats.put("newUsersToday", newUsersToday != null ? newUsersToday : 0);

        Integer newPostsToday = db.queryForObject(
            "SELECT COUNT(*) FROM know_posts WHERE deleted_at IS NULL AND DATE(created_at) = CURDATE()", Integer.class);
        stats.put("newPostsToday", newPostsToday != null ? newPostsToday : 0);

        Integer totalCollections = db.queryForObject(
            "SELECT COUNT(*) FROM collections", Integer.class);
        stats.put("totalCollections", totalCollections != null ? totalCollections : 0);

        Integer totalFollows = db.queryForObject(
            "SELECT COUNT(*) FROM following WHERE rel_status = 1", Integer.class);
        stats.put("totalFollows", totalFollows != null ? totalFollows : 0);

        return stats;
    }
}

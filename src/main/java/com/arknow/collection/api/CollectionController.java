package com.arknow.collection.api;

import com.arknow.knowpost.id.SnowflakeIdGenerator;
import com.arknow.knowpost.mapper.KnowPostMapper;
import com.arknow.knowpost.model.KnowPostFeedRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/collections")
public class CollectionController {

    private final JdbcTemplate db;
    private final SnowflakeIdGenerator idGen;
    private final KnowPostMapper postMapper;

    public CollectionController(JdbcTemplate db, SnowflakeIdGenerator idGen, KnowPostMapper postMapper) {
        this.db = db;
        this.idGen = idGen;
        this.postMapper = postMapper;
    }

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getClaimAsString("uid"));
        return db.queryForList(
            "SELECT c.id, c.name, c.created_at, " +
            "(SELECT COUNT(*) FROM collection_items ci WHERE ci.collection_id = c.id) AS item_count " +
            "FROM collections c WHERE c.user_id = ? ORDER BY c.created_at DESC", uid);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, String> body,
                                       @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getClaimAsString("uid"));
        long id = idGen.nextId();
        String name = body.get("name");
        db.update("INSERT INTO collections (id, user_id, name) VALUES (?, ?, ?)", id, uid, name);
        return Map.of("id", String.valueOf(id), "name", name);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getClaimAsString("uid"));
        db.update("DELETE FROM collection_items WHERE collection_id = ?", id);
        db.update("DELETE FROM collections WHERE id = ? AND user_id = ?", id, uid);
        return Map.of("success", true);
    }

    @GetMapping("/{id}/items")
    public List<Map<String, Object>> items(@PathVariable long id) {
        List<Map<String, Object>> items = db.queryForList(
            "SELECT ci.id, ci.post_id, ci.created_at FROM collection_items ci WHERE ci.collection_id = ? ORDER BY ci.created_at DESC", id);
        for (var item : items) {
            long postId = ((Number) item.get("post_id")).longValue();
            KnowPostFeedRow row = postMapper.getFeedRowById(postId);
            if (row != null) {
                item.put("title", row.getTitle());
                item.put("coverImage", parseFirstImg(row.getImgUrls()));
                item.put("authorNickname", row.getAuthorNickname());
            }
        }
        return items;
    }

    @PostMapping("/{id}/items")
    public Map<String, Boolean> addItem(@PathVariable long id, @RequestBody Map<String, String> body,
                                         @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getClaimAsString("uid"));
        long postId = Long.parseLong(body.get("postId"));
        // Verify ownership
        Integer count = db.queryForObject(
            "SELECT COUNT(*) FROM collections WHERE id = ? AND user_id = ?", Integer.class, id, uid);
        if (count == null || count == 0) return Map.of("success", false);
        try {
            db.update("INSERT INTO collection_items (id, collection_id, post_id) VALUES (?, ?, ?)",
                idGen.nextId(), id, postId);
        } catch (Exception e) { /* duplicate */ }
        return Map.of("success", true);
    }

    @DeleteMapping("/{id}/items/{postId}")
    public Map<String, Boolean> removeItem(@PathVariable long id, @PathVariable long postId,
                                            @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getClaimAsString("uid"));
        db.update("DELETE ci FROM collection_items ci JOIN collections c ON ci.collection_id = c.id " +
            "WHERE ci.collection_id = ? AND ci.post_id = ? AND c.user_id = ?", id, postId, uid);
        return Map.of("success", true);
    }

    private String parseFirstImg(String imgUrls) {
        if (imgUrls == null || imgUrls.isBlank() || "[]".equals(imgUrls)) return null;
        try {
            List<String> imgs = new com.fasterxml.jackson.databind.ObjectMapper().readValue(imgUrls, List.class);
            return imgs.isEmpty() ? null : imgs.getFirst().toString();
        } catch (Exception e) { return null; }
    }
}

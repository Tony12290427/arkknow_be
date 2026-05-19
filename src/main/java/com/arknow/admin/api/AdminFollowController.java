package com.arknow.admin.api;

import com.arknow.relation.mapper.RelationMapper;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/follows")
public class AdminFollowController {

    private final RelationMapper relationMapper;

    public AdminFollowController(RelationMapper relationMapper) {
        this.relationMapper = relationMapper;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<Map<String, Object>> items = relationMapper.listAllFollowing(offset, size);
        long total = relationMapper.countAllFollowing();
        return Map.of("items", items, "total", total, "page", page, "size", size);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable long id) {
        int rows = relationMapper.deleteFollowingById(id);
        return Map.of("success", rows > 0);
    }

    @PostMapping("/batch-delete")
    public Map<String, Object> batchDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("ids");
        int count = 0;
        for (int id : ids) {
            count += relationMapper.deleteFollowingById(id);
        }
        return Map.of("success", true, "deleted", count);
    }
}

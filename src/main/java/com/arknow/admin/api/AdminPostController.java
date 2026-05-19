package com.arknow.admin.api;

import com.arknow.common.exception.BusinessException;
import com.arknow.common.exception.ErrorCode;
import com.arknow.knowpost.mapper.KnowPostMapper;
import com.arknow.knowpost.model.KnowPost;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/posts")
public class AdminPostController {
    private final KnowPostMapper knowPostMapper;

    public AdminPostController(KnowPostMapper knowPostMapper) {
        this.knowPostMapper = knowPostMapper;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String status) {
        int offset = (page - 1) * size;
        List<KnowPost> posts;
        int total;
        if (status != null && !status.isBlank()) {
            posts = knowPostMapper.listByStatus(status, offset, size);
            total = knowPostMapper.countByStatus(status);
        } else {
            posts = knowPostMapper.listAll(offset, size);
            total = knowPostMapper.countAll();
        }
        return Map.of("items", posts, "total", total, "page", page, "size", size);
    }

    @GetMapping("/{id}")
    public KnowPost detail(@PathVariable long id) {
        return knowPostMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "帖子不存在"));
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable long id) {
        knowPostMapper.softDeleteAdmin(id);
        return Map.of("success", true);
    }

    @PostMapping("/batch-delete")
    public Map<String, Object> batchDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("ids");
        int count = 0;
        for (int id : ids) {
            count += knowPostMapper.softDeleteAdmin(id);
        }
        return Map.of("success", true, "deleted", count);
    }

    @PostMapping("/{id}/audit")
    public Map<String, Boolean> audit(@PathVariable long id, @RequestBody Map<String, Object> body) {
        String action = (String) body.get("action"); // "approve" or "reject"
        KnowPost post = knowPostMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "帖子不存在"));
        if ("approve".equals(action)) {
            knowPostMapper.updateStatus(id, "published");
        } else if ("reject".equals(action)) {
            knowPostMapper.updateStatus(id, "rejected");
        }
        return Map.of("success", true);
    }
}

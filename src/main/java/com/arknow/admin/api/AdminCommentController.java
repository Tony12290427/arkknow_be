package com.arknow.admin.api;

import com.arknow.comment.mapper.CommentMapper;
import com.arknow.comment.model.Comment;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/comments")
public class AdminCommentController {
    private final CommentMapper commentMapper;

    public AdminCommentController(CommentMapper commentMapper) {
        this.commentMapper = commentMapper;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<Comment> items = commentMapper.listAll(offset, size);
        int total = commentMapper.countAll();
        return Map.of("items", items, "total", total, "page", page, "size", size);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable long id) {
        int rows = commentMapper.deleteByIdAdmin(id);
        return Map.of("success", rows > 0);
    }

    @PostMapping("/batch-delete")
    public Map<String, Object> batchDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("ids");
        int count = 0;
        for (int id : ids) {
            count += commentMapper.deleteByIdAdmin(id);
        }
        return Map.of("success", true, "deleted", count);
    }
}

package com.arknow.admin.api;

import com.arknow.knowpost.id.SnowflakeIdGenerator;
import com.arknow.notification.mapper.NotificationMapper;
import com.arknow.notification.model.Notification;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationController {

    private final NotificationMapper notificationMapper;
    private final SnowflakeIdGenerator idGen;

    public AdminNotificationController(NotificationMapper notificationMapper, SnowflakeIdGenerator idGen) {
        this.notificationMapper = notificationMapper;
        this.idGen = idGen;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<Notification> items = notificationMapper.listAll(offset, size);
        long total = notificationMapper.countAll();
        return Map.of("items", items, "total", total, "page", page, "size", size);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        long id = idGen.nextId();
        long userId = Long.parseLong(String.valueOf(body.get("userId")));
        String type = (String) body.get("type");
        long actorId = Long.parseLong(String.valueOf(body.get("actorId")));
        Long postId = body.containsKey("postId") ? Long.parseLong(String.valueOf(body.get("postId"))) : null;
        Long commentId = body.containsKey("commentId") ? Long.parseLong(String.valueOf(body.get("commentId"))) : null;
        notificationMapper.insertSystem(id, userId, type, actorId, postId, commentId);
        return Map.of("success", true, "id", String.valueOf(id));
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable long id) {
        int rows = notificationMapper.deleteByIdAdmin(id);
        return Map.of("success", rows > 0);
    }

    @PostMapping("/batch-delete")
    public Map<String, Object> batchDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("ids");
        int count = 0;
        for (int id : ids) {
            count += notificationMapper.deleteByIdAdmin(id);
        }
        return Map.of("success", true, "deleted", count);
    }
}

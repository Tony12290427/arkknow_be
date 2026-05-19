package com.arknow.admin.api;

import com.arknow.counter.service.CounterService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/likes")
public class AdminLikeController {

    private final CounterService counterService;

    public AdminLikeController(CounterService counterService) {
        this.counterService = counterService;
    }

    @GetMapping
    public Map<String, Object> info() {
        return Map.of(
            "message", "点赞数据由 Redis 计数器管理（CounterService），不持久化到 MySQL。",
            "hint", "可通过 CounterService.getCounts(entityType, entityId, metrics) 查询点赞数"
        );
    }

    @DeleteMapping
    public Map<String, Object> infoDelete() {
        return Map.of(
            "message", "点赞数据在 Redis 中，如需清空请使用 redis-cli 操作对应 key。",
            "hint", "Redis key pattern: counter:like:*"
        );
    }
}

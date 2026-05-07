package com.arknow.counter.api;

import com.arknow.counter.service.CounterService;
import com.arknow.counter.service.UserCounterService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Counter query REST controller.
 */
@RestController
@RequestMapping("/api/v1/counter")
public class CounterController {
    private final CounterService counterService;
    private final UserCounterService userCounterService;

    public CounterController(CounterService counterService, UserCounterService userCounterService) {
        this.counterService = counterService;
        this.userCounterService = userCounterService;
    }

    /**
     * Returns counts for the specified metrics on an entity.
     * Example: GET /api/v1/counter/knowpost/123?metrics=like,fav
     */
    @GetMapping("/{etype}/{eid}")
    public Map<String, Object> getCounts(@PathVariable String etype,
                                          @PathVariable String eid,
                                          @RequestParam(defaultValue = "like,fav") String metrics) {
        List<String> metricList = List.of(metrics.split(","));
        Map<String, Long> counts = counterService.getCounts(etype, eid, metricList);
        return Map.of("entityType", etype, "entityId", eid, "counts", counts);
    }

    /**
     * Returns user-dimension counters: followings, followers, posts, likedPosts, favedPosts.
     */
    @GetMapping("/user/{userId}")
    public Map<String, Object> getUserCounts(@PathVariable long userId) {
        Map<String, Long> counts = userCounterService.getUserCounts(userId);
        return Map.of("entityType", "user", "entityId", String.valueOf(userId), "counts", counts);
    }
}

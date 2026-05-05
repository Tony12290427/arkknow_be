package com.arknow.counter.api;

import com.arknow.counter.service.CounterService;
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

    public CounterController(CounterService counterService) {
        this.counterService = counterService;
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
}

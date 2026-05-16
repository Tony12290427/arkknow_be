package com.arknow.counter.replay;

import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/counter")
public class CounterRebuildController {
    private final CounterRebuildConsumer rebuildConsumer;
    private final KafkaListenerEndpointRegistry registry;

    public CounterRebuildController(CounterRebuildConsumer rebuildConsumer,
                                     KafkaListenerEndpointRegistry registry) {
        this.rebuildConsumer = rebuildConsumer;
        this.registry = registry;
    }

    @PostMapping("/rebuild")
    public Map<String, Object> startRebuild(@RequestParam(required = false) String entityType) {
        try {
            rebuildConsumer.start();
            return Map.of(
                "status", "started",
                "groupId", "counter-rebuild",
                "topic", "counter-events",
                "entityType", entityType != null ? entityType : "all",
                "note", "Consumer started from earliest. Monitor /actuator/metrics/counter.rebuild.*"
            );
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    @GetMapping("/rebuild/status")
    public Map<String, Object> status() {
        var container = registry.getListenerContainer(CounterRebuildConsumer.LISTENER_ID);
        return Map.of(
            "running", container != null && container.isRunning(),
            "groupId", "counter-rebuild"
        );
    }
}

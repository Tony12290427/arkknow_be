package com.arknow.counter.service.impl;

import com.arknow.counter.service.CounterService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stub implementation of the counter service.
 * <p>
 * In the full implementation, this is replaced by a Redis SDS-based counter system
 * with Kafka async write aggregation and sharded-bitmap-based idempotency.
 * The stub returns zeros so that the knowpost feed/detail endpoints can function
 * during early development phases without the full counting infrastructure.
 */
@Service
public class CounterServiceImpl implements CounterService {

    @Override
    public Map<String, Long> getCounts(String entityType, String entityId, List<String> metrics) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String m : metrics) result.put(m, 0L);
        return result;
    }

    @Override
    public boolean isLiked(String entityType, String entityId, long userId) {
        return false;
    }

    @Override
    public boolean isFaved(String entityType, String entityId, long userId) {
        return false;
    }
}

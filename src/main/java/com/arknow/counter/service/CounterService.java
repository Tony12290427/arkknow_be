package com.arknow.counter.service;

import java.util.List;
import java.util.Map;

public interface CounterService {
    Map<String, Long> getCounts(String entityType, String entityId, List<String> metrics);

    /** Batch read SDS counters via Redis pipeline — one round trip for all entityIds. */
    Map<String, Map<String, Long>> batchGetCounts(String entityType, List<String> entityIds, List<String> metrics);

    boolean isLiked(String entityType, String entityId, long userId);
    boolean isFaved(String entityType, String entityId, long userId);
}

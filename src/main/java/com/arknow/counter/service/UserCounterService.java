package com.arknow.counter.service;

import java.util.Map;

/**
 * User-dimension counter service.
 */
public interface UserCounterService {
    Map<String, Long> getUserCounts(long userId);
    void incrementFollowings(long userId, int delta);
    void incrementFollowers(long userId, int delta);
}

package com.arknow.counter.service;

import java.util.Map;

/**
 * User-dimension counter service.
 */
public interface UserCounterService {
    Map<String, Long> getUserCounts(long userId);
    void incrementFollowings(long userId, int delta);
    void incrementFollowers(long userId, int delta);
    void incrementPosts(long userId, int delta);
    void incrementLikedPosts(long userId, int delta);
    void incrementFavedPosts(long userId, int delta);
}

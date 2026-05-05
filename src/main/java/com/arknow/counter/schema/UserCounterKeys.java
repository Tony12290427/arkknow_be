package com.arknow.counter.schema;

/**
 * Redis key patterns for user-dimension counters.
 * <p>
 * Each user has a single SDS key holding all personal metrics (followings, followers,
 * posts, likedPosts, favedPosts).
 * <p>
 * Key pattern: {@code ucnt:<userId>}
 */
public final class UserCounterKeys {

    public static String sdsKey(long userId) {
        return "ucnt:" + userId;
    }

    private UserCounterKeys() {}
}

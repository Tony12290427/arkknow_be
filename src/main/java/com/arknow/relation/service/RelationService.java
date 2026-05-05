package com.arknow.relation.service;

import com.arknow.profile.api.dto.ProfileResponse;

import java.util.List;

/**
 * User relationship service — follow, unfollow, and relationship queries.
 */
public interface RelationService {
    /** Follow another user. Returns true if a new relationship was created. */
    boolean follow(long fromUserId, long toUserId);
    /** Unfollow a user. Returns true if the relationship was removed. */
    boolean unfollow(long fromUserId, long toUserId);
    /** Returns the relationship status between two users. */
    RelationStatus getStatus(long fromUserId, long toUserId);

    List<ProfileResponse> listFollowing(long userId, int limit, int offset);
    List<ProfileResponse> listFollowers(long userId, int limit, int offset);

    /**
     * Three-state relationship status between two users.
     */
    record RelationStatus(boolean following, boolean followedBy, boolean mutual) {
        public static RelationStatus none() {
            return new RelationStatus(false, false, false);
        }
    }
}

package com.arknow.relation.event;

/**
 * Domain event representing a change in a user relationship.
 * <p>
 * Immutable record carrying the event type, the two user IDs, and the relationship ID.
 * This is serialized to JSON and stored in the Outbox table, then consumed
 * asynchronously to update follower projections, counters, and caches.
 */
public record RelationEvent(
        String type,
        long fromUserId,
        long toUserId,
        Long id
) {
    public RelationEvent {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Event type must not be blank");
        }
    }
}

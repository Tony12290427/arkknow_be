package com.arknow.relation.processor;

import com.arknow.relation.event.RelationEvent;
import com.arknow.relation.mapper.RelationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Processes {@link RelationEvent} instances to update async projections.
 * <p>
 * In production, this is invoked by a Kafka consumer listening to the Canal outbox topic.
 * For the MVP without Kafka/Canal, it is called synchronously in the same thread
 * after the following table write succeeds.
 * <p>
 * Projections updated:
 * <ul>
 *   <li>Follower table (mirror of following)</li>
 *   <li>Redis sorted sets for following/follower list caching</li>
 * </ul>
 * Each event is deduplicated via a Redis key to guarantee idempotency.
 */
@Component
public class RelationEventProcessor {
    private static final Logger log = LoggerFactory.getLogger(RelationEventProcessor.class);

    private final RelationMapper relationMapper;
    private final StringRedisTemplate redis;

    public RelationEventProcessor(RelationMapper relationMapper, StringRedisTemplate redis) {
        this.relationMapper = relationMapper;
        this.redis = redis;
    }

    /**
     * Processes a relationship event idempotently.
     * <p>
     * Dedup key: {@code dedup:rel:<type>:<fromUserId>:<toUserId>:<id>}.
     * If the key already exists, the event is skipped (already processed).
     */
    public void process(RelationEvent evt) {
        String dk = "dedup:rel:" + evt.type() + ":" + evt.fromUserId() + ":" + evt.toUserId() + ":" + (evt.id() != null ? evt.id() : "0");
        Boolean first = redis.opsForValue().setIfAbsent(dk, "1", Duration.ofMinutes(10));
        if (first == null || !first) return;

        if ("FollowCreated".equals(evt.type())) {
            // Async insert into follower projection
            relationMapper.insertFollower(evt.id(), evt.toUserId(), evt.fromUserId(), 1);
            // Update Redis sorted set caches
            long now = System.currentTimeMillis();
            redis.opsForZSet().add("uf:flws:" + evt.fromUserId(), String.valueOf(evt.toUserId()), now);
            redis.opsForZSet().add("uf:fans:" + evt.toUserId(), String.valueOf(evt.fromUserId()), now);
            redis.expire("uf:flws:" + evt.fromUserId(), Duration.ofHours(2));
            redis.expire("uf:fans:" + evt.toUserId(), Duration.ofHours(2));
            log.debug("Processed FollowCreated: {} -> {}", evt.fromUserId(), evt.toUserId());
        } else if ("FollowCanceled".equals(evt.type())) {
            // Async cancel in follower projection
            relationMapper.cancelFollower(evt.toUserId(), evt.fromUserId());
            // Remove from Redis sorted set caches
            redis.opsForZSet().remove("uf:flws:" + evt.fromUserId(), String.valueOf(evt.toUserId()));
            redis.opsForZSet().remove("uf:fans:" + evt.toUserId(), String.valueOf(evt.fromUserId()));
            log.debug("Processed FollowCanceled: {} -> {}", evt.fromUserId(), evt.toUserId());
        }
    }
}

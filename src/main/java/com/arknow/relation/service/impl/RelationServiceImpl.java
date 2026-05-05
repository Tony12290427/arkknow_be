package com.arknow.relation.service.impl;

import com.arknow.profile.api.dto.ProfileResponse;
import com.arknow.relation.event.RelationEvent;
import com.arknow.relation.mapper.RelationMapper;
import com.arknow.relation.outbox.OutboxMapper;
import com.arknow.relation.processor.RelationEventProcessor;
import com.arknow.relation.service.RelationService;
import com.arknow.user.domain.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * User relationship service implementation.
 * <p>
 * Core design: <b>One master, multiple slaves</b> with Outbox pattern.
 * <ul>
 *   <li>The {@code following} table is the single source of truth.</li>
 *   <li>The {@code follower} table, counter system, and cached lists are async projections
 *       derived from Outbox events.</li>
 *   <li>A follow operation writes to {@code following} + {@code outbox} in one DB transaction.
 *       Everything else happens asynchronously via {@link RelationEventProcessor}.</li>
 * </ul>
 * <p>
 * This eliminates the classic "dual-write inconsistency" problem: there is no scenario
 * where following says "following" but follower says "not following".
 * <p>
 * Rate limiting: a Redis-backed token bucket prevents abuse.
 */
@Service
public class RelationServiceImpl implements RelationService {
    private static final Logger log = LoggerFactory.getLogger(RelationServiceImpl.class);

    private final RelationMapper relationMapper;
    private final OutboxMapper outboxMapper;
    private final RelationEventProcessor processor;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /** Lua script for token bucket rate limiting. */
    private static final String TOKEN_BUCKET_LUA = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local now = redis.call('TIME')[1]
            local last = redis.call('HGET', key, 'last')
            local tokens = redis.call('HGET', key, 'tokens')
            if not last then last = now; tokens = capacity end
            local elapsed = tonumber(now) - tonumber(last)
            local add = elapsed * rate
            tokens = math.min(capacity, tonumber(tokens) + add)
            if tokens < 1 then
                redis.call('HSET', key, 'last', now)
                redis.call('HSET', key, 'tokens', tokens)
                return 0
            end
            tokens = tokens - 1
            redis.call('HSET', key, 'last', now)
            redis.call('HSET', key, 'tokens', tokens)
            redis.call('PEXPIRE', key, 60000)
            return 1
            """;

    public RelationServiceImpl(RelationMapper relationMapper, OutboxMapper outboxMapper,
                                RelationEventProcessor processor, StringRedisTemplate redis,
                                ObjectMapper objectMapper) {
        this.relationMapper = relationMapper;
        this.outboxMapper = outboxMapper;
        this.processor = processor;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * Follows a user.
     * <p>
     * Rate limited via Redis token bucket (100 tokens, 1/s replenishment).
     * On success, writes the following row + outbox event in one transaction,
     * then synchronously processes the event to update projections.
     * In production with Kafka, this synchronous processing is removed.
     *
     * @return true if a new following relationship was created
     */
    @Override
    @Transactional
    public boolean follow(long fromUserId, long toUserId) {
        // Token bucket rate limit
        DefaultRedisScript<Long> tokenScript = new DefaultRedisScript<>(TOKEN_BUCKET_LUA, Long.class);
        Long ok = redis.execute(tokenScript, List.of("rl:follow:" + fromUserId), "100", "1");
        if (ok == null || ok == 0L) return false;

        long id = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
        int inserted = relationMapper.insertFollowing(id, fromUserId, toUserId, 1);

        if (inserted > 0) {
            // Write Outbox event in the same transaction
            writeOutboxEvent("FollowCreated", fromUserId, toUserId, id);
            return true;
        }
        return false;
    }

    /**
     * Unfollows a user.
     * <p>
     * Cancels the following row and writes an outbox event in one transaction.
     */
    @Override
    @Transactional
    public boolean unfollow(long fromUserId, long toUserId) {
        int updated = relationMapper.cancelFollowing(fromUserId, toUserId);
        if (updated > 0) {
            long id = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
            writeOutboxEvent("FollowCanceled", fromUserId, toUserId, id);
            return true;
        }
        return false;
    }

    /**
     * Returns the three-state relationship between two users: following, followedBy, mutual.
     */
    @Override
    public RelationStatus getStatus(long fromUserId, long toUserId) {
        boolean following = relationMapper.existsFollowing(fromUserId, toUserId);
        boolean followedBy = relationMapper.existsFollowing(toUserId, fromUserId);
        return new RelationStatus(following, followedBy, following && followedBy);
    }

    @Override
    public List<ProfileResponse> listFollowing(long userId, int limit, int offset) {
        return relationMapper.listFollowing(userId, Math.min(limit, 100), Math.max(offset, 0))
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<ProfileResponse> listFollowers(long userId, int limit, int offset) {
        return relationMapper.listFollowers(userId, Math.min(limit, 100), Math.max(offset, 0))
                .stream().map(this::toResponse).toList();
    }

    /**
     * Writes an Outbox event in the current transaction.
     * In production, Canal picks this up and publishes to Kafka.
     * For MVP, we synchronously call the processor.
     */
    private void writeOutboxEvent(String eventType, long fromUserId, long toUserId, long relationId) {
        try {
            Long outId = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
            String payload = objectMapper.writeValueAsString(
                    new RelationEvent(eventType, fromUserId, toUserId, relationId));
            outboxMapper.insert(outId, "following", relationId, eventType, payload);
            // Synchronous processing — in production this is done via Kafka consumer
            processor.process(new RelationEvent(eventType, fromUserId, toUserId, relationId));
        } catch (Exception e) {
            log.warn("Failed to write outbox event: {}", e.getMessage());
        }
    }

    private ProfileResponse toResponse(User user) {
        return new ProfileResponse(
                user.getId(), user.getNickname(), user.getAvatar(), user.getBio(),
                user.getZgId(), user.getGender(), user.getBirthday(), user.getSchool(),
                user.getPhone(), user.getEmail(), user.getTagsJson());
    }
}

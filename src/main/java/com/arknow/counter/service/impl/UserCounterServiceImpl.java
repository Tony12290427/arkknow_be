package com.arknow.counter.service.impl;

import com.arknow.counter.schema.CounterSchema;
import com.arknow.counter.schema.UserCounterKeys;
import com.arknow.counter.service.UserCounterService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User-dimension counter service using the same SDS binary structure.
 * <p>
 * Each user has a single Redis key holding 5 int32 counters:
 * followings, followers, posts, likedPosts, favedPosts.
 */
@Service
public class UserCounterServiceImpl implements UserCounterService {
    private final StringRedisTemplate redis;

    /** Same Lua incr script as entity-level counters, reused for user counters. */
    private static final String SDS_INCR_LUA = """
            local key = KEYS[1]
            local totalLen = tonumber(ARGV[1])
            local fieldSize = tonumber(ARGV[2])
            local idx = tonumber(ARGV[3])
            local delta = tonumber(ARGV[4])
            local offset = idx * fieldSize

            local raw = redis.call('GET', key)
            if not raw then raw = string.rep('\\0', totalLen * fieldSize) end
            if #raw ~= totalLen * fieldSize then raw = string.rep('\\0', totalLen * fieldSize) end

            local val = 0
            for i = 1, fieldSize do val = val * 256 + string.byte(raw, offset + i) end
            if val >= 2147483648 then val = val - 4294967296 end
            val = val + delta
            if val < 0 then val = 0 end

            -- Build big-endian bytes (MSB first)
            local bytes = {}
            for i = 0, fieldSize - 1 do bytes[i + 1] = string.char(math.floor(val / (256 ^ (fieldSize - 1 - i))) % 256) end
            local patch = table.concat(bytes)

            redis.call('SET', key, raw:sub(1, offset) .. patch .. raw:sub(offset + fieldSize + 1))
            return val
            """;

    public UserCounterServiceImpl(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Map<String, Long> getUserCounts(long userId) {
        String key = UserCounterKeys.sdsKey(userId);
        int totalLen = CounterSchema.SCHEMA_LEN * CounterSchema.FIELD_SIZE;
        byte[] raw = getRaw(key);
        if (raw == null || raw.length != totalLen) {
            Map<String, Long> m = new LinkedHashMap<>();
            for (String name : CounterSchema.USER_NAME_TO_IDX.keySet()) m.put(name, 0L);
            return m;
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (var entry : CounterSchema.USER_NAME_TO_IDX.entrySet()) {
            int off = entry.getValue() * CounterSchema.FIELD_SIZE;
            result.put(entry.getKey(), readInt32BE(raw, off));
        }
        return result;
    }

    @Override
    public void incrementFollowings(long userId, int delta) {
        incrementField(userId, 0, delta);
    }

    @Override
    public void incrementFollowers(long userId, int delta) {
        incrementField(userId, 1, delta);
    }

    @Override
    public void incrementPosts(long userId, int delta) {
        incrementField(userId, 2, delta);
    }

    @Override
    public void incrementLikedPosts(long userId, int delta) {
        incrementField(userId, 3, delta);
    }

    @Override
    public void incrementFavedPosts(long userId, int delta) {
        incrementField(userId, 4, delta);
    }

    private void incrementField(long userId, int idx, int delta) {
        DefaultRedisScript<Long> incrScript = new DefaultRedisScript<>(SDS_INCR_LUA, Long.class);
        redis.execute(incrScript, List.of(UserCounterKeys.sdsKey(userId)),
                String.valueOf(CounterSchema.SCHEMA_LEN),
                String.valueOf(CounterSchema.FIELD_SIZE),
                String.valueOf(idx),
                String.valueOf(delta));
    }

    private byte[] getRaw(String key) {
        return redis.execute((org.springframework.data.redis.core.RedisCallback<byte[]>) connection ->
                connection.stringCommands().get(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static long readInt32BE(byte[] buf, int offset) {
        long val = ((long) (buf[offset] & 0xFF) << 24)
                | ((long) (buf[offset + 1] & 0xFF) << 16)
                | ((long) (buf[offset + 2] & 0xFF) << 8)
                | (buf[offset + 3] & 0xFF);
        if (val >= 0x80000000L) val -= 0x100000000L;
        return val;
    }
}

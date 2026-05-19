package com.arknow.counter.event;

import com.arknow.counter.schema.CounterKeys;
import com.arknow.counter.schema.CounterSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Disaster recovery counter rebuild consumer.
 * <p>
 * Consumes the {@code counter-events} topic from the earliest offset and
 * folds every historical event directly into the SDS counter. This reconstructs
 * the counter state from the event fact stream when Redis data is lost or
 * corrupted beyond what bitmap-based rebuild can fix.
 * <p>
 * Default: disabled. Set {@code counter.rebuild.enabled=true} to activate
 * (typically during incident recovery, then disabled again).
 */
@Service
@ConditionalOnProperty(name = "counter.rebuild.enabled", havingValue = "true")
public class CounterEventRebuildConsumer {
    private static final Logger log = LoggerFactory.getLogger(CounterEventRebuildConsumer.class);

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> incrScript;

    /** Lua script: directly folds a delta into an SDS counter field. */
    private static final String INCR_FIELD_LUA = """
            local cntKey = KEYS[1]
            local schemaLen = tonumber(ARGV[1])
            local fieldSize = tonumber(ARGV[2])
            local idx = tonumber(ARGV[3])
            local delta = tonumber(ARGV[4])

            local function read32be(s, off)
              local b = {string.byte(s, off+1, off+4)}
              local n = 0
              for i=1,4 do n = n * 256 + b[i] end
              return n
            end

            local function write32be(n)
              local t = {}
              for i=4,1,-1 do t[i] = n % 256; n = math.floor(n/256) end
              return string.char(unpack(t))
            end

            local cnt = redis.call('GET', cntKey)
            if not cnt then cnt = string.rep(string.char(0), schemaLen * fieldSize) end
            local off = idx * fieldSize
            local v = read32be(cnt, off) + delta
            if v < 0 then v = 0 end
            local seg = write32be(v)
            cnt = string.sub(cnt, 1, off) .. seg .. string.sub(cnt, off+fieldSize+1)
            redis.call('SET', cntKey, cnt)
            return 1
            """;

    public CounterEventRebuildConsumer(ObjectMapper objectMapper, StringRedisTemplate redis) {
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.incrScript = new DefaultRedisScript<>(INCR_FIELD_LUA, Long.class);
    }

    @KafkaListener(
            topics = CounterTopics.EVENTS,
            groupId = "counter-rebuild",
            properties = {"auto.offset.reset=earliest"}
    )
    public void onMessage(String message, Acknowledgment ack) throws Exception {
        CounterEvent evt = objectMapper.readValue(message, CounterEvent.class);
        String cntKey = CounterKeys.sdsKey(evt.entityType(), evt.entityId());
        try {
            redis.execute(incrScript, List.of(cntKey),
                    String.valueOf(CounterSchema.SCHEMA_LEN),
                    String.valueOf(CounterSchema.FIELD_SIZE),
                    String.valueOf(evt.idx()),
                    String.valueOf(evt.delta()));
            ack.acknowledge();
        } catch (Exception ex) {
            // Do not commit offset — retry on next poll
        }
    }
}

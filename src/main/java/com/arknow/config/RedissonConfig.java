package com.arknow.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson distributed lock client configuration.
 * <p>
 * Redisson provides higher-level Redis abstractions (distributed locks, semaphores,
 * rate limiters) on top of the standard Redis connection. The lock watchdog
 * automatically renews lock TTLs for long-running critical sections, preventing
 * premature lock expiry.
 */
@Configuration
public class RedissonConfig {

    /** Lock watchdog timeout in milliseconds. Default 30s. */
    @Value("${counter.rebuild.lock.watchdog-ms:30000}")
    private long lockWatchdogMs;

    @Bean
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        Config config = new Config();
        config.setLockWatchdogTimeout(lockWatchdogMs);

        String address = "redis://" + redisProperties.getHost() + ":" + redisProperties.getPort();
        SingleServerConfig single = config.useSingleServer().setAddress(address);

        if (redisProperties.getPassword() != null && !redisProperties.getPassword().isEmpty()) {
            single.setPassword(redisProperties.getPassword());
        }
        single.setDatabase(redisProperties.getDatabase());
        return Redisson.create(config);
    }
}

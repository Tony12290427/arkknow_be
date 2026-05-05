package com.arknow.cache.config;

import com.arknow.knowpost.api.dto.FeedPageResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache infrastructure configuration.
 * <p>
 * Creates two Caffeine caches with distinct purposes:
 * <ul>
 *   <li>{@code feedPublicCache} — caches complete public feed page responses</li>
 *   <li>{@code knowPostDetailCache} — caches individual post detail responses</li>
 * </ul>
 * Caffeine is chosen for local caching because it offers near-optimal hit rates
 * with the Window-TinyLFU eviction policy and negligible GC pressure.
 */
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    /**
     * Local cache for public feed pages.
     * Max 500 entries, TTL = cache.local-ttl seconds.
     */
    @Bean
    public Cache<String, FeedPageResponse> feedPublicCache(CacheProperties props) {
        return Caffeine.newBuilder()
                .maximumSize(props.getLocalMaxSize())
                .expireAfterWrite(Duration.ofSeconds(props.getLocalTtl()))
                .build();
    }

    /**
     * Local cache for post detail responses.
     * Smaller capacity since detail pages have lower aggregate traffic than feed.
     */
    @Bean
    public Cache<String, com.arknow.knowpost.api.dto.KnowPostDetailResponse> knowPostDetailCache(CacheProperties props) {
        return Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(Duration.ofSeconds(props.getLocalTtl()))
                .build();
    }
}

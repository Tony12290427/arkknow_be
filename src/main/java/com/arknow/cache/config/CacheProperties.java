package com.arknow.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration for the multi-tier caching system.
 * <p>
 * Three layers with cascading TTLs:
 * <ul>
 *   <li>L2 (Caffeine local): shortest TTL, fastest access, stores complete page responses</li>
 *   <li>L1 (Redis page): medium TTL, stores page skeleton (ID list + hasMore)</li>
 *   <li>L0 (Redis fragment): longest TTL, stores per-item metadata fragments</li>
 * </ul>
 * <p>
 * TTL hierarchy: L0 > L1 > L2, ensuring that fragments outlive pages and
 * pages outlive local cache. This prevents "L1 hit but L0 miss" assembly failures.
 */
@Data
@ConfigurationProperties(prefix = "cache")
public class CacheProperties {
    /** Public feed page TTL (seconds). */
    private int pageTtl = 60;
    /** Fragment (item-level) TTL (seconds). Longer to survive page evictions. */
    private int fragmentTtl = 120;
    /** Local Caffeine TTL (seconds). Shortest — hot pages only. */
    private int localTtl = 30;
    /** Maximum entries in Caffeine local cache. */
    private int localMaxSize = 500;

    /** Hotkey detection sub-config. */
    private final Hotkey hotkey = new Hotkey();

    @Data
    public static class Hotkey {
        /** Sliding window duration in seconds. */
        private int windowSeconds = 60;
        /** Granularity of each bucket in the window. */
        private int segmentSeconds = 10;
        /** Access count threshold for LOW tier. */
        private int levelLow = 50;
        /** Access count threshold for MEDIUM tier. */
        private int levelMedium = 200;
        /** Access count threshold for HIGH tier. */
        private int levelHigh = 500;
        /** Extra TTL added for LOW-tier hot pages (seconds). */
        private int extendLowSeconds = 20;
        /** Extra TTL added for MEDIUM-tier hot pages (seconds). */
        private int extendMediumSeconds = 60;
        /** Extra TTL added for HIGH-tier hot pages (seconds). */
        private int extendHighSeconds = 120;
    }
}

package com.arknow.cache.hotkey;

import com.arknow.cache.config.CacheProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight sliding-window hotkey detector.
 * <p>
 * Each cache key is tracked with an array of integer counters — one per time segment.
 * On each access, the counter for the current segment is atomically incremented.
 * The sum across all segments is the key's "heat" (access count in the recent window).
 * <p>
 * A periodic rotation task advances the current segment pointer and zeroes out
 * the new segment across all tracked keys, implementing the sliding window.
 * <p>
 * Design trade-off: this is <b>per-node local detection</b>, not distributed.
 * It is sufficient for identifying hot pages within a single instance. For
 * cluster-wide hotkey coordination, a central detector (e.g. JD HotKey) is needed.
 */
@Component
public class HotKeyDetector {

    private final int segments;
    private final CacheProperties properties;
    private final ConcurrentHashMap<String, int[]> counters = new ConcurrentHashMap<>();
    private final AtomicInteger current = new AtomicInteger(0);

    public HotKeyDetector(CacheProperties properties) {
        this.properties = properties;
        int segSeconds = Math.max(1, properties.getHotkey().getSegmentSeconds());
        int winSeconds = Math.max(1, properties.getHotkey().getWindowSeconds());
        this.segments = Math.max(1, winSeconds / segSeconds);
    }

    /** Records one access to the given cache key. O(1) atomic increment. */
    public void record(String key) {
        int[] arr = counters.computeIfAbsent(key, k -> new int[segments]);
        int idx = current.get();
        if (idx < arr.length) {
            arr[idx]++;
        }
    }

    /** Computes total accesses in the current sliding window. */
    public int heat(String key) {
        int[] arr = counters.get(key);
        if (arr == null) return 0;
        int sum = 0;
        for (int v : arr) sum += v;
        return sum;
    }

    /** Resets the heat counters for a key (e.g. after cache invalidation). */
    public void reset(String key) {
        int[] arr = counters.get(key);
        if (arr != null) Arrays.fill(arr, 0);
    }

    /**
     * Maps a heat value to a TTL extension tier.
     * <p>
     * Thresholds are configurable and checked in descending order
     * so that a key meeting HIGH also qualifies for HIGH (not LOW).
     */
    public Level level(String key) {
        int h = heat(key);
        CacheProperties.Hotkey cfg = properties.getHotkey();
        if (h >= cfg.getLevelHigh()) return Level.HIGH;
        if (h >= cfg.getLevelMedium()) return Level.MEDIUM;
        if (h >= cfg.getLevelLow()) return Level.LOW;
        return Level.NONE;
    }

    /**
     * Returns the TTL extension (seconds) for a given heat level.
     * Used to dynamically extend cache TTLs for hot pages.
     */
    public int extendSeconds(Level l) {
        CacheProperties.Hotkey cfg = properties.getHotkey();
        return switch (l) {
            case HIGH -> cfg.getExtendHighSeconds();
            case MEDIUM -> cfg.getExtendMediumSeconds();
            case LOW -> cfg.getExtendLowSeconds();
            default -> 0;
        };
    }

    /**
     * Periodic window rotation. Runs every {@code segmentSeconds} seconds.
     * Advances the pointer and zeroes the new segment for all tracked keys.
     */
    @Scheduled(fixedRateString = "${cache.hotkey.segment-seconds:10}000")
    public void rotate() {
        int next = (current.get() + 1) % segments;
        current.set(next);
        for (int[] arr : counters.values()) {
            if (next < arr.length) arr[next] = 0;
        }
    }

    public enum Level { NONE, LOW, MEDIUM, HIGH }
}

package com.arknow.counter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Counter module configuration.
 * <p>
 * Enables Spring's {@code @Scheduled} support for the periodic aggregation flush task
 * that folds Redis Hash aggregation buckets into SDS fixed-structure counters.
 */
@Configuration
@EnableScheduling
public class CounterConfig {
}

package com.arknow.counter.schema;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "counter.bucket")
public class CounterBucketConfig {
    private int entityBuckets = 100;
    private int userBuckets = 50;

    public int getEntityBuckets() { return entityBuckets; }
    public void setEntityBuckets(int v) { this.entityBuckets = v; }
    public int getUserBuckets() { return userBuckets; }
    public void setUserBuckets(int v) { this.userBuckets = v; }

    public int getBucketCount(String entityType) {
        return "user".equals(entityType) ? userBuckets : entityBuckets;
    }
}

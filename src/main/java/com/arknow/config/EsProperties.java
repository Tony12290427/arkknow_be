package com.arknow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Elasticsearch connection properties.
 * <p>
 * Binds the {@code spring.elasticsearch.*} namespace. In dev mode without ES,
 * the {@code uris} list remains empty and the {@link ElasticsearchConfig} skips
 * creating the client bean.
 */
@Data
@ConfigurationProperties(prefix = "spring.elasticsearch")
public class EsProperties {
    /** ES node URIs, e.g. {@code http://localhost:9200}. */
    private List<String> uris;
    /** Optional basic auth username. */
    private String username;
    /** Optional basic auth password. */
    private String password;

    /** Returns the first URI as a host string, or null if not configured. */
    public String getHost() {
        return (uris == null || uris.isEmpty()) ? null : uris.getFirst();
    }
}

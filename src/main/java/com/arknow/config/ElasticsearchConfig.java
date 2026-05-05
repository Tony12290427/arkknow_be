package com.arknow.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Elasticsearch client configuration.
 * <p>
 * Only activated when {@code spring.elasticsearch.uris} is configured, keeping
 * the application startable without a running ES instance during development.
 */
@Configuration
@EnableConfigurationProperties(EsProperties.class)
@ConditionalOnProperty(name = "spring.elasticsearch.uris")
public class ElasticsearchConfig {

    private final EsProperties props;

    public ElasticsearchConfig(EsProperties props) {
        this.props = props;
    }

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        BasicCredentialsProvider creds = new BasicCredentialsProvider();
        if (StringUtils.hasText(props.getUsername())) {
            creds.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(props.getUsername(), props.getPassword()));
        }

        RestClient restClient = RestClient.builder(HttpHost.create(props.getHost()))
                .setHttpClientConfigCallback(hc -> hc.setDefaultCredentialsProvider(creds))
                .build();

        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}

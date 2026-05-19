package com.arknow.storage.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OssConfig {
    private OSS ossClient;

    @Bean
    public OSS ossClient(OssProperties ossProperties) {
        this.ossClient = new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret());
        return this.ossClient;
    }

    @PreDestroy
    public void shutdown() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }
}

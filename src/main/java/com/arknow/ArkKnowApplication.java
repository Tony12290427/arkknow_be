package com.arknow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
    basePackages = "com.arknow",
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX,
            pattern = "com\\.arknow\\.llm\\..*"),
        @ComponentScan.Filter(type = FilterType.REGEX,
            pattern = "com\\.arknow\\.relation\\.outbox\\..*"),
        @ComponentScan.Filter(type = FilterType.REGEX,
            pattern = "com\\.arknow\\.knowpost\\.api\\.KnowPost(Ai|Rag)Controller")
    }
)
public class ArkKnowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArkKnowApplication.class, args);
    }
}

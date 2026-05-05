package com.arknow.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM integration configuration.
 * <p>
 * Creates a {@link ChatClient} bean backed by the DeepSeek ChatModel.
 * Only activated when a ChatModel bean exists (i.e., when the DeepSeek
 * auto-configuration successfully creates one from the configured API key).
 */
@Configuration
@ConditionalOnBean(ChatModel.class)
public class LlmConfig {

    /**
     * Builds a ChatClient using the DeepSeek ChatModel.
     * The {@code @Qualifier} ensures we bind to the DeepSeek model specifically,
     * not any other ChatModel that might be on the classpath.
     */
    @Bean
    public ChatClient chatClient(@Qualifier("deepSeekChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}

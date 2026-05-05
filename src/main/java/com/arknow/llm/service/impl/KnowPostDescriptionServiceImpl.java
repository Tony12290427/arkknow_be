package com.arknow.llm.service.impl;

import com.arknow.common.exception.BusinessException;
import com.arknow.common.exception.ErrorCode;
import com.arknow.llm.service.KnowPostDescriptionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.text.Normalizer;

/**
 * DeepSeek-backed implementation of {@link KnowPostDescriptionService}.
 * <p>
 * Uses a low-temperature prompt to generate a factual, concise summary.
 * The system prompt instructs the model to output only the description text
 * with no preamble or explanation.
 * <p>
 * Only activated when a {@link ChatClient} bean is available.
 */
@Service
@ConditionalOnBean(ChatClient.class)
public class KnowPostDescriptionServiceImpl implements KnowPostDescriptionService {

    private final ChatClient chatClient;

    public KnowPostDescriptionServiceImpl(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Generates a Chinese description not exceeding 50 characters.
     * <p>
     * The system prompt constrains the model to act as a Chinese editor.
     * Temperature is set to 0.8 to allow some stylistic variation while
     * keeping the output grounded in the source text.
     */
    @Override
    public String generateDescription(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "正文内容不能为空");
        }
        String system = "你是中文文案编辑。请基于用户提供的知文正文，生成一个中文描述，简洁有吸引力，且不超过50个汉字。不输出解释或多段，只输出结果。";
        String user = "正文如下：\n\n" + content + "\n\n请直接给出不超过50字的中文描述。";

        try {
            String result = chatClient
                    .prompt()
                    .system(system)
                    .user(user)
                    .options(DeepSeekChatOptions.builder()
                            .model("deepseek-chat")
                            .temperature(0.8)
                            .maxTokens(120)
                            .build())
                    .call()
                    .content();
            return postProcess(result);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "大模型调用失败: " + e.getMessage());
        }
    }

    /**
     * Cleans the model output: collapses whitespace, strips wrapping quotes,
     * and truncates to at most 50 code points.
     */
    private String postProcess(String text) {
        if (text == null) return "";
        String t = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("\r\n|\r|\n", " ")
                .replaceAll("\\s+", " ")
                .trim();
        // Strip wrapping quotes and trailing punctuation
        t = t.replaceAll("^[\"'\\u201c\\u201d\\u2018\\u2019]+|[\"'\\u201c\\u201d\\u2018\\u2019]+$", "")
             .replaceAll("[。!！?？；;、]+$", "");

        int limit = 50;
        int count = t.codePointCount(0, t.length());
        if (count <= limit) return t;

        StringBuilder sb = new StringBuilder();
        int i = 0, added = 0;
        while (i < t.length() && added < limit) {
            int cp = t.codePointAt(i);
            sb.appendCodePoint(cp);
            i += Character.charCount(cp);
            added++;
        }
        return sb.toString();
    }
}

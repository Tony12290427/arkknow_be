package com.arknow.search.service;

import com.arknow.counter.service.CounterService;
import com.arknow.llm.MarkdownRenderer;
import com.arknow.knowpost.mapper.KnowPostMapper;
import com.arknow.knowpost.model.KnowPostFeedRow;
import com.arknow.search.api.dto.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiSearchService {
    private static final Logger log = LoggerFactory.getLogger(AiSearchService.class);

    private static final String SYSTEM_PROMPT =
        "你是「知舟」知识社区的 AI 搜索助手。根据社区内的文章内容回答用户的问题。\n\n" +
        "Markdown 格式要求（必须严格遵守）：\n" +
        "- 标题格式：## 标题 或 ### 标题（# 和文字之间必须有空格）\n" +
        "- 表格格式：| 列1 | 列2 |（竖线两边加空格，表头分隔行用 |---|---|）\n" +
        "- 列表格式：- 项目（- 后面加空格）\n" +
        "- 粗体：**文字**（前后不留空格）\n" +
        "\n" +
        "回答规则：\n" +
        "1. 基于提供的文章片段进行回答，引用文章标题和关键信息。\n" +
        "2. 如果没有找到相关文章，诚实告知并给出建议。\n" +
        "3. 回答要简洁有条理，用规范的 Markdown 格式组织。\n" +
        "4. 每条引用标注文章标题。";

    private static final double SIMILARITY_THRESHOLD = 0.68;
    private static final int MAX_CHUNKS = 8;

    private final Optional<VectorStore> vectorStore;
    private final ChatClient chatClient;
    private final KnowPostMapper knowPostMapper;
    private final CounterService counterService;
    private final MarkdownRenderer markdownRenderer;

    private final SearchService searchService;

    public AiSearchService(Optional<VectorStore> vectorStore, ChatClient chatClient,
                           KnowPostMapper knowPostMapper, CounterService counterService,
                           MarkdownRenderer markdownRenderer, SearchService searchService) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.knowPostMapper = knowPostMapper;
        this.counterService = counterService;
        this.markdownRenderer = markdownRenderer;
        this.searchService = searchService;
    }

    public Flux<String> searchStream(String query, int topK) {
        // 1. ES keyword search — full coverage of all published articles
        List<SearchResponse> esResults = searchService.search(query, null, 1, Math.min(topK * 2, 20));

        if (esResults.isEmpty()) {
            return Flux.just("未找到相关内容，请尝试其他关键词。", "[ARTICLES]{\"items\":[]}", "[DONE]");
        }

        // 2. Build article list from ES results
        List<Long> articleIds = esResults.stream()
            .map(r -> Long.parseLong(r.id()))
            .collect(Collectors.toList());
        List<KnowPostFeedRow> rows = knowPostMapper.listFeedByIds(articleIds);

        // Build article JSON
        StringBuilder articlesJson = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            KnowPostFeedRow r = rows.get(i);
            if (i > 0) articlesJson.append(",");
            String img = parseFirstImg(r.getImgUrls());
            Map<String, Long> counts = counterService.getCounts("knowpost", r.getId(), List.of("like", "fav"));
            long likeCount = counts.getOrDefault("like", 0L);
            long favCount = counts.getOrDefault("fav", 0L);
            articlesJson.append("{")
                .append("\"id\":\"").append(r.getId()).append("\",")
                .append("\"title\":\"").append(escapeJson(r.getTitle())).append("\",")
                .append("\"description\":\"").append(escapeJson(r.getDescription())).append("\",")
                .append("\"coverImage\":").append(img != null ? "\"" + escapeJson(img) + "\"" : "null").append(",")
                .append("\"authorNickname\":\"").append(escapeJson(r.getAuthorNickname())).append("\",")
                .append("\"likeCount\":").append(likeCount).append(",")
                .append("\"favoriteCount\":").append(favCount).append(",")
                .append("\"liked\":false,\"faved\":false")
                .append("}");
        }
        articlesJson.append("]");

        // 3. Build prompt context from article titles + descriptions (full coverage)
        StringBuilder context = new StringBuilder();
        int articleCount = 0;
        for (KnowPostFeedRow r : rows) {
            if (articleCount >= topK) break;
            String title = r.getTitle() != null ? r.getTitle() : "无标题";
            String desc = r.getDescription() != null ? r.getDescription() : "";
            context.append("### ").append(title).append("\n");
            if (!desc.isBlank()) context.append(desc).append("\n\n");
            articleCount++;
        }

        // 4. Supplement with vector chunks if available (for deeper context)
        if (vectorStore.isPresent()) {
            List<Document> vectorDocs = searchVector(query, topK * 2);
            Set<String> esIds = articleIds.stream().map(String::valueOf).collect(Collectors.toSet());
            int extraChunks = 0;
            for (Document d : vectorDocs) {
                if (extraChunks >= 3) break;
                Object pid = d.getMetadata().get("postId");
                if (pid != null && esIds.contains(String.valueOf(pid))) {
                    context.append(d.getText()).append("\n\n");
                    extraChunks++;
                }
            }
        }

        String userPrompt = "用户问题：" + query + "\n\n相关文章内容：\n" + context.toString()
            + "\n请基于以上文章内容回答用户问题。";

        // 5. Stream LLM answer + render HTML at end + article list
        Flux<String> answerStream = chatClient.prompt()
            .system(SYSTEM_PROMPT)
            .user(userPrompt)
            .options(DeepSeekChatOptions.builder()
                .model("deepseek-v4-pro")
                .temperature(0.6)
                .maxTokens(1024)
                .build())
            .stream()
            .content();

        StringBuilder rawText = new StringBuilder();
        Flux<String> answerWithHtml = answerStream
            .doOnNext(rawText::append)
            .concatWith(Flux.defer(() -> {
                String html = markdownRenderer.render(rawText.toString());
                return Flux.just("[HTML]" + html.replace("\n", ""));
            }));

        Flux<String> articles = Flux.just(
            "[ARTICLES]" + articlesJson.toString(),
            "[DONE]"
        );

        return Flux.concat(answerWithHtml, articles);
    }

    private List<Document> searchVector(String query, int fetchK) {
        try {
            return vectorStore.orElseThrow().similaritySearch(
                SearchRequest.builder()
                    .query(query)
                    .topK(fetchK)
                    .similarityThreshold(SIMILARITY_THRESHOLD)
                    .build());
        } catch (Exception e) {
            log.warn("Vector search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String parseFirstImg(String imgUrls) {
        if (imgUrls == null || imgUrls.isBlank() || "[]".equals(imgUrls)) return null;
        try {
            if (imgUrls.startsWith("[")) {
                List<?> list = new com.fasterxml.jackson.databind.ObjectMapper().readValue(imgUrls, List.class);
                return list.isEmpty() ? null : String.valueOf(list.get(0));
            }
        } catch (Exception ignored) {}
        return imgUrls;
    }
}

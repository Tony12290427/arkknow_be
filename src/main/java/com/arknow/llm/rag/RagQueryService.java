package com.arknow.llm.rag;

import com.arknow.knowpost.mapper.KnowPostMapper;
import com.arknow.knowpost.model.KnowPostDetailRow;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RagQueryService {

    private static final String SYSTEM_PROMPT =
        "你是「知舟」知识社区的 AI 助手，帮助用户理解和学习社区内的技术文章。回答遵循以下规则：\n\n" +
        "1. 当用户提供了文章上下文且与问题相关时，优先基于上下文准确回答，引用文章中的具体信息。\n" +
        "2. 当没有提供上下文、或上下文与问题明显不相关时，你可以使用自身知识正常回答。不要声称信息来自文章，也不要编造文章中没有的具体数据。\n" +
        "3. 当被问到身份问题时，简短介绍自己是知舟社区的 AI 助手，可以帮用户学习文章内容。\n" +
        "4. 禁止使用「上下文为空」「内容未提及」「上下文太短」「文章没有提及」「无法确定」等机械模板话术。即使缺少上下文，也请基于你的知识给出有帮助的回答。\n" +
        "5. 回答应简洁、有条理、准确。";

    private static final double SIMILARITY_THRESHOLD = 0.72;

    private final Optional<VectorStore> vectorStore;
    private final ChatClient chatClient;
    private final RagIndexService indexService;
    private final KnowPostMapper knowPostMapper;

    public RagQueryService(Optional<VectorStore> vectorStore, ChatClient chatClient,
                            RagIndexService indexService, KnowPostMapper knowPostMapper) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.indexService = indexService;
        this.knowPostMapper = knowPostMapper;
    }

    public Flux<String> streamAnswerFlux(long postId, String question, int topK, int maxTokens) {
        String userPrompt;

        if (vectorStore.isPresent()) {
            indexService.ensureIndexed(postId);
            List<String> contexts = searchContexts(String.valueOf(postId), question, Math.max(1, topK));
            String context = String.join("\n\n---\n\n", contexts);

            if (!context.isBlank()) {
                userPrompt = "文章相关内容：\n" + context + "\n\n问题：" + question + "\n请基于以上文章内容回答。";
            } else {
                userPrompt = "问题：" + question + "\n(未找到相关文章内容，请用你自己的知识回答，不要声称来自文章。)";
            }
        } else {
            KnowPostDetailRow post = knowPostMapper.findDetailById(postId);
            if (post != null && post.getDescription() != null && !post.getDescription().isBlank()) {
                String context = "文章标题：" + (post.getTitle() != null ? post.getTitle() : "无标题") + "\n"
                        + "文章摘要：" + post.getDescription();
                userPrompt = "文章相关内容：\n" + context + "\n\n问题：" + question + "\n请基于以上文章内容回答。";
            } else {
                userPrompt = "问题：" + question;
            }
        }

        return chatClient
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .options(DeepSeekChatOptions.builder()
                        .model("deepseek-v4-pro")
                        .temperature(0.6)
                        .maxTokens(maxTokens)
                        .build())
                .stream()
                .content();
    }

    private List<String> searchContexts(String postId, String query, int topK) {
        try {
            int fetchK = Math.max(topK * 3, 20);
            List<Document> docs = vectorStore.orElseThrow().similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(fetchK)
                            .similarityThreshold(SIMILARITY_THRESHOLD)
                            .build());
            List<String> out = new ArrayList<>(topK);
            for (Document d : docs) {
                Object pid = d.getMetadata().get("postId");
                if (pid != null && postId.equals(String.valueOf(pid))) {
                    String txt = d.getText();
                    if (txt != null && !txt.isEmpty()) {
                        out.add(txt);
                        if (out.size() >= topK) break;
                    }
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}

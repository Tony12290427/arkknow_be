package com.arknow.llm.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG query service — semantic search + LLM streaming answer.
 * <p>
 * Flow:
 * <ol>
 *   <li>Ensure the target post is indexed (no-op if already current)</li>
 *   <li>Vector-search the post's chunks for contexts relevant to the question</li>
 *   <li>Assemble a prompt: system constraints + retrieved contexts + user question</li>
 *   <li>Stream the LLM answer back as Server-Sent Events via {@link Flux}</li>
 * </ol>
 * <p>
 * <b>Wide-recall strategy</b>: the vector search fetches 3x the requested topK
 * (minimum 20) to maximize recall, then filters by postId in application code.
 * This avoids the precision loss that occurs when ES filters before vector search.
 */
@Service
@ConditionalOnBean(VectorStore.class)
public class RagQueryService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final RagIndexService indexService;

    public RagQueryService(VectorStore vectorStore, ChatClient chatClient,
                            RagIndexService indexService) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.indexService = indexService;
    }

    /**
     * Answers a question about a specific post using RAG.
     *
     * @param postId    the knowledge post ID
     * @param question  the user's question in natural language
     * @param topK      number of relevant chunks to include as context
     * @param maxTokens maximum output tokens for the answer
     * @return a Flux of answer text chunks (SSE stream)
     */
    public Flux<String> streamAnswerFlux(long postId, String question, int topK, int maxTokens) {
        indexService.ensureIndexed(postId);

        List<String> contexts = searchContexts(String.valueOf(postId), question, Math.max(1, topK));
        String context = String.join("\n\n---\n\n", contexts);

        String system = "你是中文知识助手。只能依据提供的知文上下文回答；无法确定的请说明不确定。";
        String user = "问题：" + question + "\n\n上下文如下（可能不完整）：\n" + context + "\n\n请基于以上上下文作答。";

        return chatClient
                .prompt()
                .system(system)
                .user(user)
                .options(DeepSeekChatOptions.builder()
                        .model("deepseek-chat")
                        .temperature(0.2)    // Low temperature: factual over creative
                        .maxTokens(maxTokens)
                        .build())
                .stream()
                .content();
    }

    /**
     * Wide-recall semantic search followed by postId filtering.
     * <p>
     * Fetches fetchK = max(topK * 3, 20) candidates from the vector store,
     * then keeps only those belonging to the target post.
     */
    private List<String> searchContexts(String postId, String query, int topK) {
        int fetchK = Math.max(topK * 3, 20);
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(fetchK).build());
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
    }
}

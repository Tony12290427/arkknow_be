package com.arknow.knowpost.api;

import com.arknow.llm.rag.RagIndexService;
import com.arknow.llm.rag.RagQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * RAG knowledge Q&A REST controller.
 * <p>
 * Provides per-post RAG question answering with streaming (SSE) output.
 * Only activated when the full RAG stack (VectorStore + ChatClient) is available.
 */
@RestController
@RequestMapping("/api/v1/knowposts")
@Validated
@ConditionalOnBean(RagQueryService.class)
public class KnowPostRagController {

    private final RagIndexService indexService;
    private final RagQueryService ragQueryService;

    public KnowPostRagController(RagIndexService indexService, RagQueryService ragQueryService) {
        this.indexService = indexService;
        this.ragQueryService = ragQueryService;
    }

    /**
     * Streams a RAG answer for a question about a specific post.
     * Uses Server-Sent Events so the user sees text appear token-by-token.
     *
     * @param id        the knowledge post ID
     * @param question  natural-language question
     * @param topK      how many relevant chunks to retrieve (default 5)
     * @param maxTokens output limit (default 1024)
     */
    @GetMapping(value = "/{id}/qa/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> qaStream(@PathVariable("id") long id,
                                  @RequestParam("question") String question,
                                  @RequestParam(value = "topK", defaultValue = "5") int topK,
                                  @RequestParam(value = "maxTokens", defaultValue = "1024") int maxTokens) {
        return ragQueryService.streamAnswerFlux(id, question, topK, maxTokens);
    }

    /**
     * Manually triggers re-indexing for a post. Returns the number of chunks indexed.
     */
    @PostMapping("/{id}/rag/reindex")
    public int reindex(@PathVariable("id") long id) {
        return indexService.reindexSinglePost(id);
    }
}

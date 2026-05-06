package com.arknow.knowpost.api;

import com.arknow.llm.rag.RagIndexService;
import com.arknow.llm.rag.RagQueryService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/knowposts")
@Validated
public class KnowPostRagController {

    private final RagIndexService indexService;
    private final RagQueryService ragQueryService;

    public KnowPostRagController(RagIndexService indexService, RagQueryService ragQueryService) {
        this.indexService = indexService;
        this.ragQueryService = ragQueryService;
    }

    @GetMapping(value = "/{id}/qa/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> qaStream(@PathVariable("id") long id,
                                  @RequestParam("question") String question,
                                  @RequestParam(value = "topK", defaultValue = "5") int topK,
                                  @RequestParam(value = "maxTokens", defaultValue = "1024") int maxTokens) {
        return ragQueryService.streamAnswerFlux(id, question, topK, maxTokens);
    }

    @PostMapping("/{id}/rag/reindex")
    public int reindex(@PathVariable("id") long id) {
        return indexService.reindexSinglePost(id);
    }
}

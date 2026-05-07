package com.arknow.search.api;

import com.arknow.search.api.dto.SearchResponse;
import com.arknow.search.api.dto.SuggestResponse;
import com.arknow.search.service.AiSearchService;
import com.arknow.search.service.SearchService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Search REST API controller.
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
    private final SearchService searchService;
    private final AiSearchService aiSearchService;

    public SearchController(SearchService searchService, AiSearchService aiSearchService) {
        this.searchService = searchService;
        this.aiSearchService = aiSearchService;
    }

    @GetMapping
    public List<SearchResponse> search(@RequestParam String keyword,
                                        @RequestParam(required = false) String tag,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return searchService.search(keyword, tag, page, size);
    }

    @GetMapping("/suggest")
    public SuggestResponse suggest(@RequestParam String prefix,
                                    @RequestParam(defaultValue = "5") int size) {
        return searchService.suggest(prefix, size);
    }

    /**
     * AI-powered semantic search with streaming answer.
     * Searches across all posts via vector similarity, then streams an LLM-generated
     * answer with article cards appended at the end.
     */
    @GetMapping(value = "/ai", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> aiSearch(@RequestParam String q,
                                  @RequestParam(defaultValue = "5") int topK) {
        return aiSearchService.searchStream(q.trim(), topK);
    }
}

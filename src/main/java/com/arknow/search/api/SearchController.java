package com.arknow.search.api;

import com.arknow.search.api.dto.SearchResponse;
import com.arknow.search.api.dto.SuggestResponse;
import com.arknow.search.service.SearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Search REST API controller.
 * <p>
 * Provides full-text keyword search with optional tag filtering and
 * prefix-based completion suggestions for the search input box.
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Full-text search across post titles, descriptions, and tags.
     * Results are ranked by BM25 relevance blended with like count.
     */
    @GetMapping
    public List<SearchResponse> search(@RequestParam String keyword,
                                        @RequestParam(required = false) String tag,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return searchService.search(keyword, tag, page, size);
    }

    /**
     * Prefix-based completion suggestions for typeahead search.
     * Uses an FST (finite state transducer) for O(1) lookup latency.
     */
    @GetMapping("/suggest")
    public SuggestResponse suggest(@RequestParam String prefix,
                                    @RequestParam(defaultValue = "5") int size) {
        return searchService.suggest(prefix, size);
    }
}

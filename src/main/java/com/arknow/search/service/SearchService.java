package com.arknow.search.service;

import com.arknow.search.api.dto.SearchResponse;
import com.arknow.search.api.dto.SuggestResponse;

import java.util.List;

/**
 * Full-text search and prefix suggestion service.
 * <p>
 * Uses Elasticsearch with BM25 relevance scoring blended with business metrics
 * (like count) via function_score queries. Supports search_after cursor-based
 * deep pagination and completion suggester for low-latency prefix matching.
 */
public interface SearchService {
    /**
     * Full-text search with keyword matching and optional tag filtering.
     *
     * @param keyword  search query text
     * @param tag      optional tag filter (null = no filter)
     * @param page     page number (1-based)
     * @param size     results per page (max 50)
     */
    List<SearchResponse> search(String keyword, String tag, int page, int size);

    /**
     * Prefix-based completion suggestions for the search box.
     *
     * @param prefix user-typed prefix
     * @param size   max suggestions to return
     */
    SuggestResponse suggest(String prefix, int size);
}

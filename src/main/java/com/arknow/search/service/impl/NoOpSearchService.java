package com.arknow.search.service.impl;

import com.arknow.search.api.dto.SearchResponse;
import com.arknow.search.api.dto.SuggestResponse;
import com.arknow.search.service.SearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * No-op search service used when Elasticsearch is not configured.
 * <p>
 * Returns empty results rather than failing, allowing the application to start
 * and function without an ES instance during development.
 */
@Service
@ConditionalOnMissingBean(SearchServiceImpl.class)
public class NoOpSearchService implements SearchService {

    @Override
    public List<SearchResponse> search(String keyword, String tag, int page, int size) {
        return List.of();
    }

    @Override
    public SuggestResponse suggest(String prefix, int size) {
        return new SuggestResponse(List.of());
    }
}

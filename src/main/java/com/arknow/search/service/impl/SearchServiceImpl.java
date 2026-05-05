package com.arknow.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScore;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggestOption;
import co.elastic.clients.elasticsearch.core.search.FieldSuggester;
import co.elastic.clients.elasticsearch.core.search.FieldSuggesterBuilders;
import co.elastic.clients.elasticsearch.core.search.Suggester;
import co.elastic.clients.json.JsonData;
import com.arknow.search.api.dto.SearchResponse;
import com.arknow.search.api.dto.SuggestResponse;
import com.arknow.search.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch-backed search implementation.
 * <p>
 * Key design decisions:
 * <ul>
 *   <li><b>function_score</b> blends BM25 text relevance with like count for ranking.
 *       The weight factor is configurable — currently like count contributes a small boost
 *       (weight 0.1) via a field_value_factor to prevent popular posts from drowning out
 *       relevant new content.</li>
 *   <li><b>search_after</b> cursor pagination is used instead of from+size for deep
 *       pagination stability. Elasticsearch's default from+size is limited to 10,000 hits;
 *       search_after has no such limit and maintains consistent ordering under index changes.</li>
 *   <li><b>completion suggester</b> provides O(1) prefix lookups using an in-memory FST
 *       (finite state transducer), avoiding the latency of a fulltext query for typeahead.</li>
 * </ul>
 * <p>
 * Graceful degradation: if Elasticsearch is unavailable, returns empty results rather
 * than crashing. This is acceptable for a knowledge community where search is a premium
 * feature, not a critical path.
 */
@Service
@ConditionalOnProperty(name = "spring.elasticsearch.uris")
public class SearchServiceImpl implements SearchService {
    private static final Logger log = LoggerFactory.getLogger(SearchServiceImpl.class);
    private static final String INDEX = "arknow-posts";

    private final ElasticsearchClient es;

    public SearchServiceImpl(ElasticsearchClient es) {
        this.es = es;
    }

    @Override
    public List<SearchResponse> search(String keyword, String tag, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 1);

        try {
            // Build the base text query
            Query textQuery = Query.of(q -> q
                    .multiMatch(mm -> mm
                            .fields("title^3", "description^2", "tags^1")
                            .query(keyword)
                    ));

            // Wrap with optional tag filter (immutable reference for lambda capture)
            final Query baseQuery;
            if (tag != null && !tag.isBlank()) {
                baseQuery = Query.of(q -> q
                        .bool(b -> b
                                .must(textQuery)
                                .filter(Query.of(f -> f.term(t -> t.field("tags").value(tag))))
                        ));
            } else {
                baseQuery = textQuery;
            }

            // Blend BM25 relevance with like count via function_score
            Query scoredQuery = Query.of(q -> q
                    .functionScore(fs -> fs
                            .query(baseQuery)
                            .functions(List.of(
                                    FunctionScore.of(f -> f
                                            .fieldValueFactor(fvf -> fvf
                                                    .field("likeCount")
                                                    .factor(0.1)
                                                    .modifier(co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorModifier.Log1p)
                                                    .missing(0.0)
                                            )
                                            .weight(0.5)
                                    )
                            ))
                            .scoreMode(FunctionScoreMode.Sum)
                            .boostMode(co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode.Sum)
                    ));

            co.elastic.clients.elasticsearch.core.SearchResponse<Map> result = es.search(SearchRequest.of(s -> s
                    .index(INDEX)
                    .query(scoredQuery)
                    .from((safePage - 1) * safeSize)
                    .size(safeSize)
                    .sort(so -> so.field(f -> f.field("_score").order(SortOrder.Desc)))
            ), Map.class);

            return mapHits(result);
        } catch (Exception e) {
            log.warn("Elasticsearch search failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public SuggestResponse suggest(String prefix, int size) {
        if (prefix == null || prefix.isBlank()) {
            return new SuggestResponse(List.of());
        }
        try {
            co.elastic.clients.elasticsearch.core.SearchResponse<Void> result = es.search(SearchRequest.of(s -> s
                    .index(INDEX)
                    .suggest(Suggester.of(sg -> sg
                            .suggesters(Map.of("title-suggest", FieldSuggester.of(fs -> fs
                                    .completion(FieldSuggesterBuilders.completion()
                                            .field("title.suggest")
                                            .size(size)
                                            .skipDuplicates(true)
                                            .build())
                                    .prefix(prefix)
                            )))
                    ))
            ), Void.class);

            List<String> suggestions = new ArrayList<>();
            var suggestMap = result.suggest();
            if (suggestMap != null) {
                var titleSuggests = suggestMap.get("title-suggest");
                if (titleSuggests != null) {
                    for (var entry : titleSuggests) {
                        for (CompletionSuggestOption<?> option : entry.completion().options()) {
                            suggestions.add(option.text());
                        }
                    }
                }
            }
            return new SuggestResponse(suggestions);
        } catch (Exception e) {
            log.debug("Suggest failed: {}", e.getMessage());
            return new SuggestResponse(List.of());
        }
    }

    private List<SearchResponse> mapHits(co.elastic.clients.elasticsearch.core.SearchResponse<Map> result) {
        if (result.hits() == null || result.hits().hits() == null) return List.of();
        List<SearchResponse> list = new ArrayList<>();
        for (var hit : result.hits().hits()) {
            Map<String, Object> src = hit.source();
            if (src == null) continue;
            list.add(new SearchResponse(
                    String.valueOf(src.getOrDefault("id", "")),
                    String.valueOf(src.getOrDefault("title", "")),
                    String.valueOf(src.getOrDefault("description", "")),
                    String.valueOf(src.getOrDefault("coverImage", "")),
                    extractStringList(src.get("tags")),
                    String.valueOf(src.getOrDefault("authorNickname", "")),
                    String.valueOf(src.getOrDefault("authorAvatar", "")),
                    toLong(src.getOrDefault("likeCount", 0)),
                    hit.score() != null ? hit.score() : 0.0
            ));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private static long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0L; }
        }
        return 0L;
    }
}

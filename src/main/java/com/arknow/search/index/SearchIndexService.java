package com.arknow.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import com.arknow.knowpost.mapper.KnowPostMapper;
import com.arknow.knowpost.model.KnowPostDetailRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Elasticsearch index management and document indexing for search.
 * <p>
 * Maintains a search-optimized index ({@value INDEX}) that is a projection of the
 * MySQL know_posts table. Documents are indexed when posts are published and removed
 * on soft-delete.
 * <p>
 * Index mapping:
 * <ul>
 *   <li>{@code title} — text with keyword sub-field, 3x boost in multi_match</li>
 *   <li>{@code description} — text, 2x boost</li>
 *   <li>{@code tags} — keyword array with text sub-field</li>
 *   <li>{@code title.suggest} — completion type for prefix suggestions</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(name = "spring.elasticsearch.uris")
public class SearchIndexService {
    private static final Logger log = LoggerFactory.getLogger(SearchIndexService.class);
    private static final String INDEX = "zhizhou-posts";

    private final ElasticsearchClient es;
    private final KnowPostMapper knowPostMapper;

    public SearchIndexService(ElasticsearchClient es, KnowPostMapper knowPostMapper) {
        this.es = es;
        this.knowPostMapper = knowPostMapper;
    }

    /**
     * Indexes a single post document. Used in event-driven indexing when a post is published.
     */
    public void indexPost(long postId) {
        KnowPostDetailRow row = knowPostMapper.findDetailById(postId);
        if (row == null || !"published".equals(row.getStatus())) return;

        try {
            Map<String, Object> doc = toDocument(row);
            es.index(i -> i.index(INDEX).id(String.valueOf(postId)).document(doc));
            log.debug("Indexed post {}", postId);
        } catch (Exception e) {
            log.warn("Failed to index post {}: {}", postId, e.getMessage());
        }
    }

    /**
     * Removes a post from the search index (e.g. on soft-delete).
     */
    public void deletePost(long postId) {
        try {
            es.delete(d -> d.index(INDEX).id(String.valueOf(postId)));
        } catch (Exception e) {
            log.warn("Failed to delete post {} from index: {}", postId, e.getMessage());
        }
    }

    /**
     * Transforms a KnowPostDetailRow into an ES document with appropriate field mappings.
     */
    private Map<String, Object> toDocument(KnowPostDetailRow row) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", row.getId());
        doc.put("title", row.getTitle());
        doc.put("title.suggest", Map.of("input", extractTitleTerms(row.getTitle())));
        doc.put("description", row.getDescription() != null ? row.getDescription() : "");
        doc.put("tags", parseTags(row.getTags()));
        doc.put("authorNickname", row.getAuthorNickname());
        doc.put("authorAvatar", row.getAuthorAvatar());
        doc.put("likeCount", row.getLikeCount() != null ? row.getLikeCount() : 0);
        doc.put("publishTime", row.getPublishTime());
        return doc;
    }

    /** Extracts individual terms from a title for completion suggester input. */
    private static List<String> extractTitleTerms(String title) {
        if (title == null || title.isBlank()) return List.of();
        List<String> terms = new ArrayList<>();
        terms.add(title);
        // Split CJK titles into overlapping bigrams for prefix matching
        if (title.matches(".*[\\u4e00-\\u9fff].*")) {
            for (int i = 0; i < title.length() - 1; i++) {
                terms.add(title.substring(i, Math.min(i + 3, title.length())));
            }
        }
        return terms;
    }

    /** Parses JSON array string of tags into a Java List. */
    private static List<String> parseTags(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) return List.of();
        if (json.startsWith("[")) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, List.class);
            } catch (Exception e) {
                return List.of();
            }
        }
        return List.of(json);
    }
}

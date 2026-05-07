package com.arknow.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import com.arknow.counter.service.CounterService;
import com.arknow.knowpost.mapper.KnowPostMapper;
import com.arknow.knowpost.model.KnowPostDetailRow;
import com.arknow.knowpost.model.KnowPostFeedRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Elasticsearch search index management.
 * <p>
 * Maintains a search-optimized ES index ({@value INDEX}) as a projection of MySQL
 * know_posts. Supports:
 * <ul>
 *   <li><b>Upsert</b>: full document indexing with field mapping, count enrichment,
 *       and completion suggester input</li>
 *   <li><b>Soft delete</b>: updates document status to "deleted" without removing</li>
 *   <li><b>Backfill</b>: on startup, if index is empty, bulk-indexes all published posts</li>
 * </ul>
 * Content charset detection handles both UTF-8 and GB18030 encoded pages from OSS.
 */
@Service
@ConditionalOnProperty(name = "spring.elasticsearch.uris")
public class SearchIndexService {
    private static final Logger log = LoggerFactory.getLogger(SearchIndexService.class);
    private static final String INDEX = "arknow-posts";

    private final ElasticsearchClient es;
    private final KnowPostMapper knowPostMapper;
    private final CounterService counterService;
    private final ObjectMapper objectMapper;
    private final RestTemplate http = new RestTemplate();

    public SearchIndexService(ElasticsearchClient es, KnowPostMapper knowPostMapper,
                               CounterService counterService, ObjectMapper objectMapper) {
        this.es = es;
        this.knowPostMapper = knowPostMapper;
        this.counterService = counterService;
        this.objectMapper = objectMapper;
    }

    /** On startup, backfill the index if empty. */
    @PostConstruct
    public void ensureBackfill() {
        try {
            long cnt = es.count(c -> c.index(INDEX)).count();
            if (cnt > 0) return;
            int limit = 500, offset = 0;
            while (true) {
                List<KnowPostFeedRow> rows = knowPostMapper.listFeedPublic(limit, offset);
                if (rows == null || rows.isEmpty()) break;
                for (KnowPostFeedRow r : rows) upsertKnowPost(Long.parseLong(r.getId()));
                offset += rows.size();
            }
            log.info("Search index backfill completed: {} docs", es.count(c -> c.index(INDEX)).count());
        } catch (Exception e) {
            log.warn("Search index backfill skipped: {}", e.getMessage());
        }
    }

    /** Full document upsert with field enrichment and completion suggester. */
    public void upsertKnowPost(long id) {
        try {
            KnowPostDetailRow row = knowPostMapper.findDetailById(id);
            if (row == null) return;

            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("content_id", row.getId());
            doc.put("content_type", row.getType());
            doc.put("title", row.getTitle());
            doc.put("description", row.getDescription());
            doc.put("author_nickname", row.getAuthorNickname());
            doc.put("author_avatar", row.getAuthorAvatar());
            doc.put("author_tag_json", row.getAuthorTagJson());
            doc.put("tags", parseArray(row.getTags()));
            doc.put("status", row.getStatus());
            // Extract first cover image from img_urls JSON array
            String coverImage = parseFirstImg(row.getImgUrls());
            if (coverImage != null) doc.put("coverImage", coverImage);
            if (row.getIsTop() != null) doc.put("is_top", row.getIsTop());

            String body = fetchContentSafe(row.getContentUrl());
            if (body == null || body.isBlank()) body = row.getDescription();
            if (body != null) doc.put("body", truncate(body, 4000));

            Map<String, Long> counts = counterService.getCounts("knowpost", String.valueOf(id), List.of("like", "fav"));
            doc.put("like_count", counts.getOrDefault("like", 0L));
            doc.put("favorite_count", counts.getOrDefault("fav", 0L));

            if (row.getTitle() != null && !row.getTitle().isBlank()) {
                doc.put("title_suggest", row.getTitle());
            }

            IndexRequest<Map<String, Object>> req = IndexRequest.of(b -> b
                    .index(INDEX).id(String.valueOf(id)).document(doc).refresh(Refresh.WaitFor));
            IndexResponse resp = es.index(req);
            log.debug("Indexed post {} result={}", id, resp.result());
        } catch (Exception e) {
            log.error("Index upsert failed for post {}: {}", id, e.getMessage());
        }
    }

    /** Soft-deletes a post from the index by setting status=deleted. */
    public void softDeleteKnowPost(long id) {
        try {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("content_id", id);
            doc.put("status", "deleted");
            IndexRequest<Map<String, Object>> req = IndexRequest.of(b -> b
                    .index(INDEX).id(String.valueOf(id)).document(doc).refresh(Refresh.WaitFor));
            es.index(req);
        } catch (Exception e) {
            log.error("Index soft delete failed for post {}: {}", id, e.getMessage());
        }
    }

    /** Downloads content with charset detection (UTF-8 / GB18030). */
    private String fetchContentSafe(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.TEXT_HTML, MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON));
            ResponseEntity<byte[]> resp = http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            byte[] bytes = resp.getBody();
            if (bytes == null || bytes.length == 0) return null;
            return new String(bytes, detectCharset(bytes, resp.getHeaders().getContentType()));
        } catch (Exception e) {
            return null;
        }
    }

    private Charset detectCharset(byte[] bytes, MediaType contentType) {
        Charset header = contentType != null ? contentType.getCharset() : null;
        if (header != null && header != StandardCharsets.ISO_8859_1 && header != StandardCharsets.US_ASCII)
            return header;
        // Try UTF-8 first, fall back to GB18030
        int utf8Errors = countReplacement(new String(bytes, StandardCharsets.UTF_8));
        if (utf8Errors == 0) return StandardCharsets.UTF_8;
        return Charset.forName("GB18030");
    }

    private int countReplacement(String s) {
        int cnt = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '�') cnt++;
        return cnt;
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    private List<?> parseArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<String>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private String parseFirstImg(String imgUrls) {
        List<?> list = parseArray(imgUrls);
        return list.isEmpty() ? null : String.valueOf(list.get(0));
    }
}

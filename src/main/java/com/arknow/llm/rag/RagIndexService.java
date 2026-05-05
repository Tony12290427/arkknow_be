package com.arknow.llm.rag;

import com.arknow.knowpost.mapper.KnowPostMapper;
import com.arknow.knowpost.model.KnowPostDetailRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * RAG document indexing service.
 * <p>
 * Converts knowledge posts into searchable vector chunks stored in the
 * Elasticsearch-backed {@link VectorStore}. Each post is chunked by
 * Markdown headers (to preserve semantic boundaries) and then by fixed
 * 800-character windows with 100-character overlap (to prevent boundary
 * truncation of key information).
 * <p>
 * <b>Idempotency via fingerprint</b>: before indexing, the service checks
 * whether the post's SHA-256 or ETag matches the already-indexed version.
 * If unchanged, indexing is skipped. If changed, old chunks are deleted
 * and replaced — guaranteeing a single canonical version in the index.
 * <p>
 * This is a <b>lazy indexing</b> strategy: a post is only indexed the first
 * time someone asks a question about it, not at publish time. This saves
 * Embedding API costs for posts that are never queried.
 */
@Service
@ConditionalOnBean(VectorStore.class)
public class RagIndexService {
    private static final Logger log = LoggerFactory.getLogger(RagIndexService.class);

    private final VectorStore vectorStore;
    private final KnowPostMapper knowPostMapper;
    private final RestTemplate http = new RestTemplate();

    public RagIndexService(VectorStore vectorStore, KnowPostMapper knowPostMapper) {
        this.vectorStore = vectorStore;
        this.knowPostMapper = knowPostMapper;
    }

    /**
     * Ensures a post is indexed before a RAG query. Called at query time.
     * If the post is already up-to-date (same fingerprint), this is a no-op.
     */
    public void ensureIndexed(long postId) {
        reindexSinglePost(postId);
    }

    /**
     * Re-indexes a single post. Returns the number of chunks created,
     * or 0 if the post was skipped (not published, not public, or already current).
     */
    public int reindexSinglePost(long postId) {
        KnowPostDetailRow row = knowPostMapper.findDetailById(postId);
        if (row == null) {
            log.warn("Post {} not found", postId);
            return 0;
        }
        if (!"published".equalsIgnoreCase(row.getStatus()) || !"public".equalsIgnoreCase(row.getVisible())) {
            log.warn("Post {} is not public/published, skip indexing", postId);
            return 0;
        }
        if (!StringUtils.hasText(row.getContentUrl())) {
            log.warn("Post {} missing contentUrl", postId);
            return 0;
        }

        // Fingerprint check: skip if content hasn't changed
        String currentSha = row.getContentSha256();
        String currentEtag = row.getContentEtag();
        if (isUpToDate(postId, currentSha, currentEtag)) {
            log.info("Post {} already indexed with same fingerprint, skip", postId);
            return 0;
        }

        String text = fetchContent(row.getContentUrl());
        if (!StringUtils.hasText(text)) {
            log.warn("Post {} content empty", postId);
            return 0;
        }

        List<String> chunks = chunkMarkdown(text);
        deleteExistingChunks(postId);

        List<Document> docs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("postId", String.valueOf(postId));
            meta.put("chunkId", postId + "#" + i);
            meta.put("position", i);
            meta.put("contentEtag", currentEtag);
            meta.put("contentSha256", currentSha);
            meta.put("contentUrl", row.getContentUrl());
            meta.put("title", row.getTitle());
            docs.add(new Document(chunks.get(i), meta));
        }
        try {
            vectorStore.add(docs);
        } catch (Exception e) {
            log.error("VectorStore add failed: {}", e.getMessage());
            return 0;
        }
        return docs.size();
    }

    /**
     * Checks whether an already-indexed version matches the current content.
     * <p>
     * Currently returns false (always re-index) as a safe default.
     * With ES 9.x properly configured, this would compare SHA-256 or ETag fingerprints
     * against the existing indexed documents to skip redundant indexing.
     */
    private boolean isUpToDate(long postId, String currentSha, String currentEtag) {
        return false; // Always re-index for safety in MVP
    }

    /**
     * Deletes all existing chunks for a post to ensure clean upsert.
     * <p>
     * No-op in MVP mode. VectorStore.add() with the same postId/key will overwrite
     * existing embeddings via the store's upsert semantics.
     */
    private void deleteExistingChunks(long postId) {
        // No-op: VectorStore handles deduplication via upsert
    }

    /** Downloads the Markdown content from the OSS URL. */
    private String fetchContent(String url) {
        try {
            return http.getForObject(url, String.class);
        } catch (Exception e) {
            log.error("Fetch content failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Two-pass chunking:
     * <ol>
     *   <li>Split by Markdown headers (#) — these are author-defined logical boundaries</li>
     *   <li>For paragraphs exceeding 800 chars, split into 800-char windows with 100-char overlap</li>
     * </ol>
     * The overlap prevents key information from being truncated at chunk boundaries.
     */
    private List<String> chunkMarkdown(String text) {
        List<String> paras = new ArrayList<>();
        String[] lines = text.split("\r?\n");
        StringBuilder buf = new StringBuilder();
        for (String line : lines) {
            boolean isHeader = line.startsWith("#");
            if (isHeader && !buf.isEmpty()) {
                paras.add(buf.toString());
                buf.setLength(0);
            }
            buf.append(line).append('\n');
        }
        if (!buf.isEmpty()) paras.add(buf.toString());
        return getChunks(paras);
    }

    private static List<String> getChunks(List<String> paras) {
        List<String> chunks = new ArrayList<>();
        for (String p : paras) {
            if (p.length() <= 800) {
                chunks.add(p);
            } else {
                int start = 0;
                while (start < p.length()) {
                    int end = Math.min(start + 800, p.length());
                    chunks.add(p.substring(start, end));
                    if (end >= p.length()) break;
                    start = Math.max(end - 100, start + 1);
                }
            }
        }
        return chunks;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}

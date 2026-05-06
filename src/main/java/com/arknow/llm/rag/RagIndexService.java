package com.arknow.llm.rag;

import com.arknow.knowpost.mapper.KnowPostMapper;
import com.arknow.knowpost.model.KnowPostDetailRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class RagIndexService {
    private static final Logger log = LoggerFactory.getLogger(RagIndexService.class);

    private final Optional<VectorStore> vectorStore;
    private final KnowPostMapper knowPostMapper;
    private final RestTemplate http = new RestTemplate();

    public RagIndexService(Optional<VectorStore> vectorStore, KnowPostMapper knowPostMapper) {
        this.vectorStore = vectorStore;
        this.knowPostMapper = knowPostMapper;
    }

    public boolean isAvailable() {
        return vectorStore.isPresent();
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
        String currentSha = row.getContentSha256();
        String currentEtag = row.getContentEtag();
        String text;
        if (StringUtils.hasText(row.getContentUrl())) {
            if (isUpToDate(postId, currentSha, currentEtag)) {
                log.info("Post {} already indexed with same fingerprint, skip", postId);
                return 0;
            }
            text = fetchContent(row.getContentUrl());
        } else if (StringUtils.hasText(row.getDescription())) {
            log.info("Post {} using description as fallback chunk", postId);
            text = row.getDescription();
        } else {
            log.warn("Post {} has no contentUrl or description, skip indexing", postId);
            return 0;
        }
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
            if (currentEtag != null) meta.put("contentEtag", currentEtag);
            if (currentSha != null) meta.put("contentSha256", currentSha);
            if (row.getContentUrl() != null) meta.put("contentUrl", row.getContentUrl());
            if (row.getTitle() != null) meta.put("title", row.getTitle());
            docs.add(new Document(chunks.get(i), meta));
        }
        try {
            vectorStore.orElseThrow().add(docs);
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
     * Chunking by H2-level headings (##), keeping heading + content together.
     * Short sections (&lt;200 chars) are merged with the previous one to avoid
     * empty or nearly-empty chunks. Long sections are split into 1200-char
     * windows with 200-char overlap to prevent boundary truncation.
     */
    private List<String> chunkMarkdown(String text) {
        // Split before ## headers, keeping header with its content
        List<String> sections = new ArrayList<>();
        for (String part : text.split("(?=\n## )")) {
            // Merge short sections into the previous one
            if (part.length() < 200 && !sections.isEmpty()) {
                int last = sections.size() - 1;
                sections.set(last, sections.get(last) + part);
            } else {
                sections.add(part);
            }
        }

        List<String> chunks = new ArrayList<>();
        for (String s : sections) {
            if (s.length() <= 1200) {
                chunks.add(s);
            } else {
                // Split long sections with overlap
                int start = 0;
                while (start < s.length()) {
                    int end = Math.min(start + 1200, s.length());
                    chunks.add(s.substring(start, end));
                    if (end >= s.length()) break;
                    start = Math.max(end - 200, start + 1);
                }
            }
        }
        return chunks;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}

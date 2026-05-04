package com.arknow.knowpost.service.impl;

import com.arknow.counter.service.CounterService;
import com.arknow.knowpost.api.dto.FeedItemResponse;
import com.arknow.knowpost.api.dto.FeedPageResponse;
import com.arknow.knowpost.mapper.KnowPostMapper;
import com.arknow.knowpost.model.KnowPostFeedRow;
import com.arknow.knowpost.service.KnowPostFeedService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class KnowPostFeedServiceImpl implements KnowPostFeedService {
    private final KnowPostMapper mapper;
    private final CounterService counterService;

    public KnowPostFeedServiceImpl(KnowPostMapper mapper, CounterService counterService) {
        this.mapper = mapper;
        this.counterService = counterService;
    }

    @Override
    public FeedPageResponse getPublicFeed(int page, int size, Long currentUserId) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 1);
        int offset = (safePage - 1) * safeSize;

        List<KnowPostFeedRow> rows = mapper.listFeedPublic(safeSize + 1, offset);
        boolean hasMore = rows.size() > safeSize;
        if (hasMore) rows = rows.subList(0, safeSize);

        List<FeedItemResponse> items = rows.stream().map(r -> toFeedItem(r, currentUserId)).toList();
        return new FeedPageResponse(items, safePage, safeSize, hasMore);
    }

    private FeedItemResponse toFeedItem(KnowPostFeedRow r, Long currentUserId) {
        List<String> imgs = parseArray(r.getImgUrls());
        long uid = currentUserId != null ? currentUserId : 0L;
        boolean liked = currentUserId != null && counterService.isLiked("knowpost", r.getId(), uid);
        boolean faved = currentUserId != null && counterService.isFaved("knowpost", r.getId(), uid);
        Map<String, Long> counts = counterService.getCounts("knowpost", r.getId(), List.of("like", "fav"));

        return new FeedItemResponse(
                r.getId(), r.getTitle(), r.getDescription(), imgs.isEmpty() ? null : imgs.getFirst(),
                parseArray(r.getTags()), r.getAuthorAvatar(), r.getAuthorNickname(),
                r.getAuthorTagJson(), counts.getOrDefault("like", 0L), counts.getOrDefault("fav", 0L),
                liked, faved, r.getIsTop());
    }

    private static List<String> parseArray(String json) {
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

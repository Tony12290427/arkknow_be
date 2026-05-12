package com.arknow.comment.api;

import com.arknow.comment.mapper.CommentMapper;
import com.arknow.comment.model.Comment;
import com.arknow.knowpost.id.SnowflakeIdGenerator;
import com.arknow.knowpost.mapper.KnowPostMapper;
import com.arknow.knowpost.model.KnowPost;
import com.arknow.notification.mapper.NotificationMapper;
import com.arknow.notification.model.Notification;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/comments")
public class CommentController {

    private final CommentMapper mapper;
    private final NotificationMapper notifMapper;
    private final KnowPostMapper postMapper;
    private final SnowflakeIdGenerator idGen;

    public CommentController(CommentMapper mapper, NotificationMapper notifMapper,
                              KnowPostMapper postMapper, SnowflakeIdGenerator idGen) {
        this.mapper = mapper;
        this.notifMapper = notifMapper;
        this.postMapper = postMapper;
        this.idGen = idGen;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam long postId,
                                     @RequestParam(defaultValue = "0") int offset,
                                     @RequestParam(defaultValue = "20") int limit) {
        List<Comment> items = mapper.listByPost(postId, offset, limit);
        int total = mapper.countByPost(postId);
        for (Comment c : items) {
            c.setReplyCount(mapper.countReplies(c.getId()));
        }
        return Map.of("items", items, "total", total, "offset", offset);
    }

    @GetMapping("/{id}/replies")
    public Map<String, Object> replies(@PathVariable long id,
                                       @RequestParam(defaultValue = "0") int offset,
                                       @RequestParam(defaultValue = "3") int limit) {
        List<Comment> items = mapper.listReplies(id, offset, limit);
        int total = mapper.countReplies(id);
        boolean hasMore = offset + limit < total;
        return Map.of("items", items, "total", total, "hasMore", hasMore);
    }

    @PostMapping
    public Comment create(@RequestBody Map<String, Object> body,
                           @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getClaimAsString("uid"));
        Comment c = new Comment();
        c.setId(idGen.nextId());
        c.setPostId(Long.parseLong(body.get("postId").toString()));
        c.setUserId(uid);
        c.setContent((String) body.get("content"));
        Object parentId = body.get("parentId");
        if (parentId != null) c.setParentId(Long.parseLong(parentId.toString()));
        mapper.insert(c);

        // Notify post author (unless commenting on own post)
        Optional<KnowPost> post = postMapper.findById(c.getPostId());
        if (post.isPresent() && !post.get().getCreatorId().equals(uid)) {
            Notification n = new Notification();
            n.setId(idGen.nextId());
            n.setUserId(post.get().getCreatorId());
            n.setType("comment");
            n.setActorId(uid);
            n.setPostId(c.getPostId());
            n.setCommentId(c.getId());
            notifMapper.insert(n);
        }

        return c;
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable long id,
                                       @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getClaimAsString("uid"));
        int rows = mapper.softDeleteById(id, uid);
        if (rows == 0) {
            throw new com.arknow.common.exception.BusinessException(
                com.arknow.common.exception.ErrorCode.BAD_REQUEST, "评论不存在或无权删除");
        }
        return Map.of("success", true);
    }
}

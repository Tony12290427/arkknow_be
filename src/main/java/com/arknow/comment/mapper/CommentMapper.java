package com.arknow.comment.mapper;

import com.arknow.comment.model.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommentMapper {
    void insert(Comment comment);
    List<Comment> listByPost(@Param("postId") long postId, @Param("offset") int offset, @Param("limit") int limit);
    int countByPost(@Param("postId") long postId);
    List<Comment> listReplies(@Param("parentId") long parentId);
    int countReplies(@Param("parentId") long parentId);
}

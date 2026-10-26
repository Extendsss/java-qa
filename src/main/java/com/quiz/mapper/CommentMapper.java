package com.quiz.mapper;

import com.quiz.entity.Comment;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface CommentMapper {
    Comment findById(Long id);
    List<Comment> list(@Param("questionId") Long questionId,@Param("offset") int offset,@Param("size") int size);
    long count(Long questionId);
    int insert(Comment comment);
    int softDelete(Long id);
}

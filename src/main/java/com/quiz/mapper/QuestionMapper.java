package com.quiz.mapper;

import com.quiz.entity.Question;
import com.quiz.entity.dto.QuestionQuery;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface QuestionMapper {
    Question findById(Long id);
    Question lockById(Long id);
    List<Question> list(QuestionQuery query);
    long count(QuestionQuery query);
    List<Question> chain(Long rootId);
    int insert(Question question);
    int update(Question question);
    int softDelete(@Param("ids") List<Long> ids);
    int setChainPublic(@Param("rootId") Long rootId, @Param("isPublic") Integer isPublic);
}

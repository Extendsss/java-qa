package com.quiz.mapper;

import com.quiz.entity.AnswerRecord;
import com.quiz.entity.dto.QuestionQuery;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

public interface PracticeMapper {
    int insert(AnswerRecord record);
    List<AnswerRecord> records(QuestionQuery query);
    long recordCount(QuestionQuery query);
    Map<String,Object> totals(QuestionQuery query);
    List<Map<String,Object>> byCategory(QuestionQuery query);
}

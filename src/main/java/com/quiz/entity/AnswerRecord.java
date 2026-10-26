package com.quiz.entity;

import java.time.LocalDateTime;

public class AnswerRecord {
    public Long id, userId, questionId, categoryId, resumeId;
    public String userAnswer, title, type, categoryName;
    public Integer isCorrect;
    public LocalDateTime createTime;
}

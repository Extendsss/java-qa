package com.quiz.entity;

import java.time.LocalDateTime;

public class Comment {
    public Long id, questionId, userId;
    public String content, nickname;
    public Integer status;
    public LocalDateTime createTime;
}

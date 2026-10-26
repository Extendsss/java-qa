package com.quiz.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Question {
    public Long id, categoryId, parentId, rootId, resumeId, blockId, ownerId;
    public String type, title, content, options, answer, analysis, categoryName, ownerName;
    public Integer difficulty, isPublic, status, followUpCount;
    public Boolean hasFollowUp;
    public LocalDateTime createTime, updateTime;
}

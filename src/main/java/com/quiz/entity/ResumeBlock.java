package com.quiz.entity;

import java.time.LocalDateTime;

public class ResumeBlock {
    public Long id;
    public Long resumeId;
    public Long blockDefId;
    public String content;
    public Integer sort;
    public Integer status;
    public LocalDateTime createTime;
    public LocalDateTime updateTime;
    public String blockName;
    public String fieldSchema;
    public Integer repeatable;
}

package com.quiz.entity;

import java.time.LocalDateTime;

public class Resume {
    public Long id;
    public String title;
    public Long ownerId;
    public Integer isPublic;
    public Integer status;
    public LocalDateTime createTime;
    public LocalDateTime updateTime;
    public String ownerName;
    public Integer blockCount;
    public Integer questionCount;
}

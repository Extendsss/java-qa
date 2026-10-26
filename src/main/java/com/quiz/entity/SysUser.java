package com.quiz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

public class SysUser {
    public Long id;
    public String username;
    @JsonIgnore public String password;
    public String nickname;
    public String role;
    public Integer status;
    public LocalDateTime createTime;
    public LocalDateTime updateTime;
}

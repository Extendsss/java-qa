package com.quiz.mapper;

import com.quiz.entity.SysUser;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {
    SysUser findById(@Param("id") Long id);
    SysUser findByUsername(@Param("username") String username);
    List<SysUser> findPage(@Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);
    long count(@Param("keyword") String keyword);
    void insert(SysUser user);
    void update(SysUser user);
    void updatePassword(@Param("id") Long id, @Param("password") String password);
}

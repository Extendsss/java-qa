package com.quiz.security;

import cn.dev33.satoken.stp.StpInterface;
import com.quiz.common.BizException;
import com.quiz.entity.SysUser;
import com.quiz.mapper.UserMapper;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StpInterfaceImpl implements StpInterface {
    private final UserMapper users;
    public StpInterfaceImpl(UserMapper users) { this.users = users; }
    public List<String> getPermissionList(Object loginId, String loginType) { return Collections.emptyList(); }
    public List<String> getRoleList(Object loginId, String loginType) {
        SysUser user = users.findById(Long.valueOf(loginId.toString()));
        if (user == null || !Integer.valueOf(1).equals(user.status)) throw new BizException(401, "请先登录");
        return Collections.singletonList(user.role);
    }
}

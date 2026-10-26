package com.quiz.security;

import cn.dev33.satoken.stp.StpUtil;
import com.quiz.common.BizException;
import com.quiz.entity.SysUser;
import com.quiz.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
    private final UserMapper users;
    public SessionService(UserMapper users) { this.users = users; }
    public SysUser current() {
        SysUser user = users.findById(StpUtil.getLoginIdAsLong());
        if (user == null || !Integer.valueOf(1).equals(user.status)) {
            StpUtil.logout();
            throw new BizException(401, "请先登录");
        }
        return user;
    }
    public boolean isAdmin() { return "ADMIN".equals(current().role); }
    public boolean visible(Long ownerId, Integer isPublic) {
        SysUser user = current();
        return "ADMIN".equals(user.role) || Integer.valueOf(1).equals(isPublic) || user.id.equals(ownerId);
    }
    public boolean canEdit(Long ownerId, Integer isPublic) {
        SysUser user = current();
        return "ADMIN".equals(user.role) || (user.id.equals(ownerId) && Integer.valueOf(0).equals(isPublic));
    }
    public void requireEdit(Long ownerId, Integer isPublic) {
        if (!canEdit(ownerId, isPublic)) throw new BizException(403, "只能编辑自己的私有内容，公共内容仅管理员可编辑");
    }
}

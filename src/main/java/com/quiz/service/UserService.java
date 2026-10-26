package com.quiz.service;

import cn.dev33.satoken.stp.StpUtil;
import com.quiz.common.BizException;
import com.quiz.common.Checks;
import com.quiz.entity.SysUser;
import com.quiz.mapper.UserMapper;
import com.quiz.security.LoginThrottle;
import com.quiz.security.SessionService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserMapper users;
    private final SessionService session;
    private final LoginThrottle throttle;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    public UserService(UserMapper users, SessionService session, LoginThrottle throttle) { this.users = users; this.session = session; this.throttle = throttle; }
    public Map<String,Object> login(String username, String password) {
        throttle.check(username);
        SysUser user = users.findByUsername(username.trim());
        if (user == null || !encoder.matches(password, user.password)) {
            throttle.failure(username);
            throw new BizException(1001, "用户名或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.status)) throw new BizException(1002, "账号已被禁用，请联系管理员");
        throttle.success(username);
        StpUtil.login(user.id);
        Map<String,Object> result = new LinkedHashMap<String,Object>();
        result.put("token", StpUtil.getTokenValue()); result.put("id",user.id); result.put("username",user.username); result.put("nickname",user.nickname); result.put("role",user.role);
        return result;
    }
    public SysUser create(String username, String password, String nickname, String role) {
        Checks.require("ADMIN".equals(role) || "USER".equals(role), "角色只能为 ADMIN 或 USER");
        if (users.findByUsername(username.trim()) != null) throw new BizException(1004,"用户名已存在");
        SysUser user = new SysUser(); user.username = username.trim(); user.password = encoder.encode(password); user.nickname = nickname == null ? username.trim() : nickname.trim(); user.role = role; user.status = 1;
        try { users.insert(user); } catch (DuplicateKeyException ex) { throw new BizException(1004,"用户名已存在"); }
        return users.findById(user.id);
    }
    @Transactional public SysUser update(Long id, String nickname, String role, Integer status) {
        SysUser target = require(id);
        Checks.require("ADMIN".equals(role) || "USER".equals(role), "角色只能为 ADMIN 或 USER");
        Checks.require(status != null && (status == 0 || status == 1), "状态只能为 0 或 1");
        if (session.current().id.equals(id) && (!"ADMIN".equals(role) || status == 0)) throw new BizException(403,"不能禁用或降级自己的账号");
        target.nickname = nickname.trim(); target.role = role; target.status = status;
        users.update(target);
        if (status == 0) StpUtil.logout(id);
        return users.findById(id);
    }
    public void resetPassword(Long id, String password) { require(id); users.updatePassword(id,encoder.encode(password)); StpUtil.logout(id); }
    public void changePassword(String oldPassword, String newPassword) {
        SysUser current = session.current();
        if (!encoder.matches(oldPassword,current.password)) throw new BizException(1009,"旧密码错误");
        users.updatePassword(current.id,encoder.encode(newPassword));
        StpUtil.logout(current.id);
    }
    private SysUser require(Long id) { SysUser user = users.findById(id); if (user == null) throw new BizException(404,"用户不存在"); return user; }
}

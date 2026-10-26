package com.quiz.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.quiz.common.Result;
import com.quiz.entity.SysUser;
import com.quiz.security.SessionService;
import com.quiz.service.UserService;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService users;
    private final SessionService session;
    public AuthController(UserService users, SessionService session) { this.users=users; this.session=session; }
    public static class LoginRequest {
        @NotBlank(message="请输入用户名") @Size(max=50,message="用户名最多50字") public String username;
        @NotBlank(message="请输入密码") @Size(max=72,message="密码最多72字符") public String password;
    }
    @PostMapping("/login") public Result<Map<String,Object>> login(@Valid @RequestBody LoginRequest request) { return Result.ok(users.login(request.username,request.password)); }
    @PostMapping("/logout") public Result<Void> logout() { StpUtil.logout(); return Result.ok(); }
    @GetMapping("/me") public Result<SysUser> me() { return Result.ok(session.current()); }
}

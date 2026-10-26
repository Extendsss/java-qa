package com.quiz.controller;

import com.quiz.common.Result;
import com.quiz.service.UserService;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {
    private final UserService users;
    public UserController(UserService users) { this.users=users; }
    public static class PasswordRequest {
        @NotBlank(message="请输入旧密码") @Size(max=72,message="密码最多72字符") public String oldPassword;
        @NotBlank(message="请输入新密码") @Size(min=6,max=72,message="新密码长度应为6～72字符") public String newPassword;
    }
    @PutMapping("/api/me/password") public Result<Void> password(@Valid @RequestBody PasswordRequest request) { users.changePassword(request.oldPassword,request.newPassword); return Result.ok(); }
    @GetMapping("/api/ping") public Result<String> ping() { return Result.ok("ok"); }
}

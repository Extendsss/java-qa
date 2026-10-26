package com.quiz.controller.admin;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.quiz.common.Page;
import com.quiz.common.PageQuery;
import com.quiz.common.Result;
import com.quiz.entity.SysUser;
import com.quiz.mapper.UserMapper;
import com.quiz.service.UserService;
import javax.validation.Valid;
import javax.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserService users;
    private final UserMapper mapper;
    public AdminUserController(UserService users, UserMapper mapper) { this.users=users; this.mapper=mapper; }
    public static class CreateRequest {
        @NotBlank(message="请输入用户名") @Pattern(regexp="[a-zA-Z0-9_\\-.]{3,50}",message="用户名为3～50位字母、数字、下划线、横线或点") public String username;
        @NotBlank(message="请输入密码") @Size(min=6,max=72,message="密码长度应为6～72字符") public String password;
        @Size(max=50,message="昵称最多50字") public String nickname;
        @NotBlank @Pattern(regexp="ADMIN|USER",message="角色只能为ADMIN或USER") public String role="USER";
    }
    public static class UpdateRequest {
        @NotNull @Size(max=50,message="昵称最多50字") public String nickname;
        @NotBlank @Pattern(regexp="ADMIN|USER",message="角色只能为ADMIN或USER") public String role;
        @NotNull @Min(0) @Max(1) public Integer status;
    }
    public static class ResetRequest { @NotBlank @Size(min=6,max=72,message="密码长度应为6～72字符") public String password; }
    @SaCheckRole("ADMIN") @GetMapping public Result<Page<SysUser>> list(@RequestParam(required=false) String keyword, @ModelAttribute PageQuery query) { return Result.ok(Page.of(mapper.count(keyword),mapper.findPage(keyword,query.getOffset(),query.getSize()))); }
    @SaCheckRole("ADMIN") @PostMapping public Result<SysUser> create(@Valid @RequestBody CreateRequest request) { return Result.ok(users.create(request.username,request.password,request.nickname,request.role)); }
    @SaCheckRole("ADMIN") @PutMapping("/{id}") public Result<SysUser> update(@PathVariable Long id,@Valid @RequestBody UpdateRequest request) { return Result.ok(users.update(id,request.nickname,request.role,request.status)); }
    @SaCheckRole("ADMIN") @PutMapping("/{id}/password") public Result<Void> password(@PathVariable Long id,@Valid @RequestBody ResetRequest request) { users.resetPassword(id,request.password); return Result.ok(); }
}

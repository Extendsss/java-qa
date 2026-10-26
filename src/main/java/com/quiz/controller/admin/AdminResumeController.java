package com.quiz.controller.admin;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.quiz.common.Page;
import com.quiz.common.Result;
import com.quiz.entity.Resume;
import com.quiz.entity.dto.ResumeRequests;
import com.quiz.service.ResumeService;
import javax.validation.Valid;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/resumes")
@SaCheckRole("ADMIN")
public class AdminResumeController {
    private final ResumeService service;
    public AdminResumeController(ResumeService service) { this.service = service; }
    @InitBinder public void directFields(WebDataBinder binder) { binder.initDirectFieldAccess(); }
    @GetMapping @SaCheckRole("ADMIN") public Result<Page<Resume>> list(@ModelAttribute ResumeRequests.Query query) { return Result.ok(service.list(query)); }
    @PutMapping("/{id}") @SaCheckRole("ADMIN") public Result<Resume> update(@PathVariable Long id, @Valid @RequestBody ResumeRequests.Update request) { return Result.ok(service.update(id, request)); }
    @PutMapping("/{id}/public") @SaCheckRole("ADMIN") public Result<Resume> visibility(@PathVariable Long id, @Valid @RequestBody ResumeRequests.Visibility request) { return Result.ok(service.setPublic(id, request.isPublic)); }
    @DeleteMapping("/{id}") @SaCheckRole("ADMIN") public Result<Void> delete(@PathVariable Long id) { service.delete(id); return Result.ok(); }
}

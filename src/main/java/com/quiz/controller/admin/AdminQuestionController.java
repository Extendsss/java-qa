package com.quiz.controller.admin;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.quiz.common.*;
import com.quiz.entity.Question;
import com.quiz.entity.dto.*;
import com.quiz.service.QuestionService;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/questions")
@SaCheckRole("ADMIN")
public class AdminQuestionController {
    private final QuestionService service;
    public AdminQuestionController(QuestionService service) { this.service=service; }
    public static class VisibilityRequest { @NotNull @Min(0) @Max(1) public Integer isPublic; }
    @GetMapping @SaCheckRole("ADMIN") public Result<Page<Question>> list(@RequestParam Map<String,String> query) { return Result.ok(service.list(QuestionQuery.from(query),true,true)); }
    @PutMapping("/{id}/public") @SaCheckRole("ADMIN") public Result<Void> visibility(@PathVariable Long id,@Valid @RequestBody VisibilityRequest request) { service.setPublic(id,request.isPublic);return Result.ok(); }
    @PutMapping("/{id}") @SaCheckRole("ADMIN") public Result<Question> update(@PathVariable Long id,@Valid @RequestBody QuestionRequest request) { return Result.ok(service.update(id,request)); }
    @DeleteMapping("/{id}") @SaCheckRole("ADMIN") public Result<Void> delete(@PathVariable Long id) { service.delete(id);return Result.ok(); }
}

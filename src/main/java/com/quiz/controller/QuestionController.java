package com.quiz.controller;

import com.quiz.common.*;
import com.quiz.entity.*;
import com.quiz.entity.dto.*;
import com.quiz.service.QuestionService;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.*;

@RestController
@RequestMapping("/api")
public class QuestionController {
    private final QuestionService service;
    public QuestionController(QuestionService service) { this.service=service; }
    @GetMapping("/questions") public Result<Page<Question>> list(@RequestParam Map<String,String> query) { return Result.ok(service.list(QuestionQuery.from(query),false,true)); }
    @GetMapping("/questions/{id}") public Result<Question> detail(@PathVariable Long id) { return Result.ok(service.requireVisible(id)); }
    @GetMapping("/questions/{id}/chain") public Result<List<Question>> chain(@PathVariable Long id,@RequestParam(defaultValue="0") int brief) { return Result.ok(service.chain(id,brief==1)); }
    @PostMapping("/questions") public Result<Question> create(@Valid @RequestBody QuestionRequest request) { return Result.ok(service.create(request)); }
    @PutMapping("/questions/{id}") public Result<Question> update(@PathVariable Long id,@Valid @RequestBody QuestionRequest request) { return Result.ok(service.update(id,request)); }
    @DeleteMapping("/questions/{id}") public Result<Void> delete(@PathVariable Long id) { service.delete(id);return Result.ok(); }
    @GetMapping("/resumes/{id}/questions") public Result<Page<Question>> resumeQuestions(@PathVariable Long id,@RequestParam Map<String,String> query) {
        return Result.ok(service.resumeQuestions(id,QuestionQuery.from(query),"1".equals(query.get("brief"))));
    }
}

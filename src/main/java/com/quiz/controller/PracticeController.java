package com.quiz.controller;

import com.quiz.common.*;
import com.quiz.entity.*;
import com.quiz.entity.dto.*;
import com.quiz.service.PracticeService;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.*;

@RestController
@RequestMapping("/api/practice")
public class PracticeController {
    private final PracticeService service;
    public PracticeController(PracticeService service) { this.service=service; }
    @PostMapping("/start") public Result<List<Question>> start(@Valid @RequestBody PracticeRequest request) { return Result.ok(service.start(request)); }
    @PostMapping("/reveal") public Result<Map<String,Object>> reveal(@Valid @RequestBody SubmitRequest request) { return Result.ok(service.reveal(request)); }
    @PostMapping("/submit") public Result<Map<String,Object>> submit(@Valid @RequestBody SubmitRequest request) { return Result.ok(service.submit(request)); }
    @GetMapping("/records") public Result<Page<AnswerRecord>> records(@RequestParam Map<String,String> query) { return Result.ok(service.records(QuestionQuery.from(query))); }
    @GetMapping("/wrong") public Result<Page<Question>> wrong(@RequestParam Map<String,String> query) { return Result.ok(service.wrong(QuestionQuery.from(query))); }
    @GetMapping("/stats") public Result<Map<String,Object>> stats() { return Result.ok(service.stats()); }
}

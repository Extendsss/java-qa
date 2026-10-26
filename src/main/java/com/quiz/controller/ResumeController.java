package com.quiz.controller;

import com.quiz.common.Page;
import com.quiz.common.Result;
import com.quiz.entity.Resume;
import com.quiz.entity.ResumeBlock;
import com.quiz.entity.dto.ResumeRequests;
import com.quiz.service.ResumeService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {
    private final ResumeService service;
    public ResumeController(ResumeService service) { this.service = service; }

    @InitBinder public void directFields(WebDataBinder binder) { binder.initDirectFieldAccess(); }

    @GetMapping public Result<Page<Resume>> list(@ModelAttribute ResumeRequests.Query query) { return Result.ok(service.list(query)); }
    @GetMapping("/{id}") public Result<Resume> detail(@PathVariable Long id) { return Result.ok(service.requireVisible(id)); }
    @PostMapping public Result<Resume> create(@Valid @RequestBody ResumeRequests.Create request) { return Result.ok(service.create(request)); }
    @PutMapping("/{id}") public Result<Resume> update(@PathVariable Long id, @Valid @RequestBody ResumeRequests.Update request) { return Result.ok(service.update(id, request)); }
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable Long id) { service.delete(id); return Result.ok(); }
    @GetMapping("/{id}/blocks") public Result<List<ResumeBlock>> blocks(@PathVariable Long id) { return Result.ok(service.blocks(id)); }
    @PostMapping("/{id}/blocks") public Result<ResumeBlock> addBlock(@PathVariable Long id, @Valid @RequestBody ResumeRequests.BlockCreate request) { return Result.ok(service.addBlock(id, request)); }
    @PutMapping("/blocks/{blockId}") public Result<ResumeBlock> updateBlock(@PathVariable Long blockId, @Valid @RequestBody ResumeRequests.BlockUpdate request) { return Result.ok(service.updateBlock(blockId, request)); }
    @DeleteMapping("/blocks/{blockId}") public Result<Void> deleteBlock(@PathVariable Long blockId) { service.deleteBlock(blockId); return Result.ok(); }
}

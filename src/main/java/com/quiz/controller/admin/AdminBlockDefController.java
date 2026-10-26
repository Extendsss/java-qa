package com.quiz.controller.admin;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.quiz.common.Result;
import com.quiz.entity.BlockDef;
import com.quiz.entity.dto.ResumeRequests;
import com.quiz.service.BlockDefService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/block-defs")
@SaCheckRole("ADMIN")
public class AdminBlockDefController {
    private final BlockDefService service;
    public AdminBlockDefController(BlockDefService service) { this.service = service; }
    @GetMapping @SaCheckRole("ADMIN") public Result<List<BlockDef>> list() { return Result.ok(service.list(false)); }
    @PostMapping @SaCheckRole("ADMIN") public Result<BlockDef> create(@Valid @RequestBody ResumeRequests.Definition request) { return Result.ok(service.create(request)); }
    @PutMapping("/{id}") @SaCheckRole("ADMIN") public Result<BlockDef> update(@PathVariable Long id, @Valid @RequestBody ResumeRequests.Definition request) { return Result.ok(service.update(id, request)); }
    @DeleteMapping("/{id}") @SaCheckRole("ADMIN") public Result<Void> delete(@PathVariable Long id) { service.delete(id); return Result.ok(); }
}

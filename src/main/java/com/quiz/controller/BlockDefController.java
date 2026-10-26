package com.quiz.controller;

import com.quiz.common.Result;
import com.quiz.entity.BlockDef;
import com.quiz.service.BlockDefService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/block-defs")
public class BlockDefController {
    private final BlockDefService service;
    public BlockDefController(BlockDefService service) { this.service = service; }
    @GetMapping public Result<List<BlockDef>> list() { return Result.ok(service.list(true)); }
}

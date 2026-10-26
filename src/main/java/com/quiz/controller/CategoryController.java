package com.quiz.controller;

import com.quiz.common.Result;
import com.quiz.entity.Category;
import com.quiz.mapper.CategoryMapper;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class CategoryController {
    private final CategoryMapper categories;
    public CategoryController(CategoryMapper categories) { this.categories=categories; }
    @GetMapping("/api/categories") public Result<List<Category>> list() { return Result.ok(categories.findAll()); }
}

package com.quiz.controller.admin;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.quiz.common.BizException;
import com.quiz.common.Result;
import com.quiz.entity.Category;
import com.quiz.mapper.CategoryMapper;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {
    private final CategoryMapper categories;
    public AdminCategoryController(CategoryMapper categories) { this.categories=categories; }
    public static class CategoryRequest { @NotBlank(message="请输入分类名称") @Size(max=50,message="分类名称最多50字") public String name; public Integer sort=0; }
    @SaCheckRole("ADMIN") @GetMapping public Result<List<Category>> list() { return Result.ok(categories.findAll()); }
    @SaCheckRole("ADMIN") @PostMapping public Result<Category> create(@Valid @RequestBody CategoryRequest request) { Category c=new Category(); c.name=request.name.trim(); c.sort=request.sort==null?0:request.sort; categories.insert(c); return Result.ok(categories.findById(c.id)); }
    @SaCheckRole("ADMIN") @PutMapping("/{id}") public Result<Category> update(@PathVariable Long id,@Valid @RequestBody CategoryRequest request) { Category c=require(id); c.name=request.name.trim();c.sort=request.sort==null?0:request.sort;categories.update(c);return Result.ok(categories.findById(id)); }
    @SaCheckRole("ADMIN") @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable Long id) { require(id); if(categories.countReferences(id)>0) throw new BizException(1005,"该分类下仍有题目，无法删除"); categories.softDelete(id);return Result.ok(); }
    private Category require(Long id) { Category c=categories.findById(id);if(c==null)throw new BizException(404,"分类不存在");return c; }
}

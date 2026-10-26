package com.quiz.mapper;

import com.quiz.entity.Category;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface CategoryMapper {
    Category findById(@Param("id") Long id);
    List<Category> findAll();
    void insert(Category category);
    void update(Category category);
    void softDelete(@Param("id") Long id);
    long countReferences(@Param("id") Long id);
}

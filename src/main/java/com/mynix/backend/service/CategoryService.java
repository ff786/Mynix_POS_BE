package com.mynix.backend.service;

import com.mynix.backend.dto.category.CategoryRequest;
import com.mynix.backend.dto.category.CategoryResponse;

import java.util.List;

public interface CategoryService {

    CategoryResponse create(CategoryRequest request);
    List<CategoryResponse> getAll();
    CategoryResponse getById(Long id);
    CategoryResponse update(Long id, CategoryRequest request);
    void delete(Long id);

}
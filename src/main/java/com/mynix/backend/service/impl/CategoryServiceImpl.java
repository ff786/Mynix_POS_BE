package com.mynix.backend.service.impl;

import com.mynix.backend.dto.category.CategoryRequest;
import com.mynix.backend.dto.category.CategoryResponse;
import com.mynix.backend.model.Category;
import com.mynix.backend.repository.CategoryRepository;
import com.mynix.backend.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    public CategoryResponse create(CategoryRequest request) {

        if (categoryRepository.existsByNameIgnoreCase(request.getName())) {
            throw new RuntimeException("Category already exists.");
        }

        Category category = Category.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .active(true)
                .build();

        category = categoryRepository.save(category);

        return mapToResponse(category);
    }

    @Override
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public CategoryResponse getById(Long id) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found."));

        return mapToResponse(category);
    }

    @Override
    public CategoryResponse update(Long id, CategoryRequest request) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found."));

        category.setName(request.getName().trim());
        category.setDescription(request.getDescription());

        category = categoryRepository.save(category);

        return mapToResponse(category);
    }

    @Override
    public void delete(Long id) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found."));

        categoryRepository.delete(category);
    }

    private CategoryResponse mapToResponse(Category category) {

        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .active(category.getActive())
                .build();
    }
}
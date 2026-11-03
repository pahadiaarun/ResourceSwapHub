package com.bookbridge.category;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.shared.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final ResourceCategoryRepository categoryRepository;

    public CategoryController(ResourceCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ResourceCategoryDto>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(categoryRepository
                .findByIsActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(ResourceCategoryDto::from)
                .toList()));
    }
}

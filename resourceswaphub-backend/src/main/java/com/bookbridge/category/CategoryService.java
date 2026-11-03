package com.bookbridge.category;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.shared.error.ApiException;

@Service
public class CategoryService {

    private final ResourceCategoryRepository categoryRepository;

    public CategoryService(ResourceCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public ResourceCategory requireById(UUID id) {
        return categoryRepository.findById(id)
                .filter(ResourceCategory::isActive)
                .orElseThrow(() -> ApiException.badRequest("INVALID_CATEGORY", "Choose a supported resource category."));
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public ResourceCategory requireBySlug(String slug) {
        return categoryRepository.findBySlugAndIsActiveTrue(resolveSlug(slug))
                .orElseThrow(() -> ApiException.badRequest("INVALID_CATEGORY", "Choose a supported resource category."));
    }

    /** Category slugs arrive URL-encoded from filters; the API accepts both forms. */
    private String resolveSlug(String slug) {
        return slug.trim().toLowerCase().replace(' ', '-');
    }
}

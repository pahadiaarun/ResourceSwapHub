package com.bookbridge.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceCategoryRepository extends JpaRepository<ResourceCategory, UUID> {

    Optional<ResourceCategory> findBySlugAndIsActiveTrue(String slug);

    Optional<ResourceCategory> findBySlug(String slug);

    List<ResourceCategory> findByIsActiveTrueOrderBySortOrderAsc();
}

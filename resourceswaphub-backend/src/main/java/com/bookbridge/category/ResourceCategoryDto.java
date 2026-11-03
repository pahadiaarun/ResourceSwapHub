package com.bookbridge.category;

import java.util.UUID;

public record ResourceCategoryDto(
        UUID id,
        String slug,
        String name,
        String description,
        short sortOrder,
        boolean allowsPhysical,
        boolean allowsDigital,
        boolean bookRequired
) {
    public static ResourceCategoryDto from(ResourceCategory category) {
        return new ResourceCategoryDto(
                category.getId(),
                category.getSlug(),
                category.getName(),
                category.getDescription(),
                category.getSortOrder(),
                category.isAllowsPhysical(),
                category.isAllowsDigital(),
                category.isBookRequired()
        );
    }
}

package com.bookbridge.alerts;

import java.time.Instant;
import java.util.UUID;

import com.bookbridge.category.ResourceCategoryDto;

public record ResourceAlertDto(
        UUID id,
        String keyword,
        ResourceCategoryDto category,
        String subject,
        String academicLevel,
        UUID stateId,
        UUID cityId,
        boolean freeOnly,
        boolean enabled,
        Instant createdAt
) {
    public static ResourceAlertDto from(ResourceAlert alert) {
        return new ResourceAlertDto(
                alert.getId(),
                alert.getKeyword(),
                alert.getCategory() == null ? null : ResourceCategoryDto.from(alert.getCategory()),
                alert.getSubject(),
                alert.getAcademicLevel(),
                alert.getStateId(),
                alert.getCityId(),
                alert.isFreeOnly(),
                alert.isEnabled(),
                alert.getCreatedAt()
        );
    }
}

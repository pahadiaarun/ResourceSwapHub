package com.bookbridge.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.bookbridge.category.ResourceCategoryDto;
import com.bookbridge.location.LocationDto;

public record ResourceRequestDto(
        UUID id,
        RequesterDto requester,
        String title,
        String description,
        ResourceCategoryDto category,
        String subject,
        String author,
        String edition,
        String academicLevel,
        String preferredCondition,
        BigDecimal priceMin,
        BigDecimal priceMax,
        boolean freeOnly,
        LocationDto location,
        String city,
        String status,
        Instant expiresAt,
        Instant fulfilledAt,
        Instant cancelledAt,
        Instant expiredAt,
        Instant createdAt,
        List<UUID> conversationIds
) {
}

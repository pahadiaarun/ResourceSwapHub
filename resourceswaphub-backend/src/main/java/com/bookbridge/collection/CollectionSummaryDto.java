package com.bookbridge.collection;

import java.time.Instant;
import java.util.UUID;

import com.bookbridge.category.ResourceCategoryDto;
import com.bookbridge.listing.SellerDto;
import com.bookbridge.location.LocationDto;

public record CollectionSummaryDto(
        UUID id,
        String title,
        String description,
        String city,
        String locality,
        LocationDto location,
        SellerDto owner,
        ResourceCategoryDto category,
        String academicLevel,
        String institution,
        String visibility,
        boolean published,
        long itemCount,
        long availableCount,
        String coverUrl,
        Instant createdAt
) {
}

package com.bookbridge.listing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.bookbridge.catalog.BookDto;
import com.bookbridge.category.ResourceCategoryDto;
import com.bookbridge.location.LocationDto;
import com.bookbridge.shared.media.MediaDto;

public record ListingDto(
        UUID id,
        ResourceCategoryDto category,
        BookDto book,
        SellerDto seller,
        String title,
        String condition,
        String conditionLabel,
        String medium,
        String priceType,
        String priceTypeLabel,
        BigDecimal price,
        String city,
        String locality,
        LocationDto location,
        String subject,
        String academicLevel,
        String board,
        String author,
        String publisher,
        String edition,
        Integer publicationYear,
        String notes,
        String externalUrl,
        MediaDto digitalFile,
        boolean ownerRightsConfirmed,
        String status,
        List<String> imageUrls,
        String clientRef,
        Instant createdAt,
        Instant updatedAt
) {
}

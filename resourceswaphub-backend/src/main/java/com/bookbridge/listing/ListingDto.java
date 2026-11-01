package com.bookbridge.listing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.bookbridge.catalog.BookDto;
import com.bookbridge.collection.CollectionRef;
import com.bookbridge.location.LocationDto;

public record ListingDto(
        UUID id,
        BookDto book,
        SellerDto seller,
        CollectionRef collection,
        String condition,
        String conditionLabel,
        String priceType,
        String priceTypeLabel,
        BigDecimal price,
        String city,
        String locality,
        LocationDto location,
        String notes,
        String status,
        List<String> imageUrls,
        Instant createdAt,
        Instant updatedAt
) {
}

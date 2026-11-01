package com.bookbridge.collection;

import java.time.Instant;
import java.util.UUID;

import com.bookbridge.listing.SellerDto;

public record CollectionSummaryDto(
        UUID id,
        String title,
        String description,
        String city,
        String locality,
        SellerDto owner,
        long itemCount,
        long availableCount,
        String coverUrl,
        Instant createdAt
) {
}

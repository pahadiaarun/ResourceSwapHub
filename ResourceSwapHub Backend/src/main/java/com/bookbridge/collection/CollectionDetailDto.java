package com.bookbridge.collection;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.bookbridge.listing.ListingDto;
import com.bookbridge.listing.SellerDto;

public record CollectionDetailDto(
        UUID id,
        String title,
        String description,
        String city,
        String locality,
        SellerDto owner,
        long itemCount,
        long availableCount,
        String coverUrl,
        Instant createdAt,
        List<ListingDto> items
) {
}

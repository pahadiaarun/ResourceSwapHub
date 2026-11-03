package com.bookbridge.saved;

import java.time.Instant;
import java.util.UUID;

import com.bookbridge.listing.ListingDto;

public record SavedResourceDto(
        UUID listingId,
        ListingDto listing,
        boolean unavailable,
        Instant savedAt
) {
    public static SavedResourceDto from(SavedResource saved, ListingDto listing) {
        return new SavedResourceDto(
                listing.id(),
                listing,
                !"AVAILABLE".equals(listing.status()),
                saved.getCreatedAt()
        );
    }
}

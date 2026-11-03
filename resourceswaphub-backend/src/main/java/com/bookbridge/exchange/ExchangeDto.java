package com.bookbridge.exchange;

import java.time.Instant;
import java.util.UUID;

import com.bookbridge.listing.SellerDto;

/**
 * Public-safe exchange record: references and status only. Conversation content
 * and contact details never appear here.
 */
public record ExchangeDto(
        UUID id,
        UUID listingId,
        String listingTitle,
        String listingStatus,
        UUID ownerId,
        SellerDto owner,
        SellerDto participant,
        boolean viewerIsOwner,
        String status,
        Instant confirmedAt,
        Instant createdAt,
        boolean viewerConfirmed
) {
}

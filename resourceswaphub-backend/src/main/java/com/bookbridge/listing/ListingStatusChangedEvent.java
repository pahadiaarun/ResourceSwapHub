package com.bookbridge.listing;

import java.util.UUID;

/** Published whenever a listing's status changes so event listeners stay in the same transaction. */
public record ListingStatusChangedEvent(UUID listingId, ListingStatus previous, ListingStatus current) {
}

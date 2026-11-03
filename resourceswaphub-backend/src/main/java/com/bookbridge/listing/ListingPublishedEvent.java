package com.bookbridge.listing;

import java.util.List;
import java.util.UUID;

/**
 * Published when a listing becomes AVAILABLE (creation or republish). Alerts and
 * other listeners react with {@code BEFORE_COMMIT} listeners so everything
 * either commits or rolls back together — no scheduler needed.
 */
public record ListingPublishedEvent(UUID listingId) {
}

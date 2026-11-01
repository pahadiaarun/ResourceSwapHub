package com.bookbridge.listing;

import jakarta.validation.constraints.NotNull;

public record ListingStatusUpdateRequest(@NotNull(message = "Choose a status") ListingStatus status) {
}

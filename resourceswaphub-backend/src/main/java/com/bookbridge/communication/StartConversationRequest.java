package com.bookbridge.communication;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record StartConversationRequest(@NotNull(message = "Choose a listing") UUID listingId) {
}

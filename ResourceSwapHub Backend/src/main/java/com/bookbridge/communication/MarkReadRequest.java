package com.bookbridge.communication;

import jakarta.validation.constraints.NotNull;

public record MarkReadRequest(@NotNull(message = "Message ID is required") Long lastMessageId) {
}

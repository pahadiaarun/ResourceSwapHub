package com.bookbridge.communication;

import java.util.UUID;

public record ChatEventPayload(
        String type,
        UUID conversationId,
        MessageDto message,
        long unreadCount
) {
}

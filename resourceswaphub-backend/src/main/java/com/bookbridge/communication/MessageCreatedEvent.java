package com.bookbridge.communication;

import java.util.List;
import java.util.UUID;

public record MessageCreatedEvent(UUID conversationId, MessageDto message, List<UUID> participantIds) {
}

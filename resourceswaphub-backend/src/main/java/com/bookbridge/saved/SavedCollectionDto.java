package com.bookbridge.saved;

import java.time.Instant;
import java.util.UUID;

public record SavedCollectionDto(
        UUID collectionId,
        String title,
        String visibility,
        Instant savedAt
) {
}

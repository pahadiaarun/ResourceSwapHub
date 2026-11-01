package com.bookbridge.knowledge;

import java.util.UUID;

public record UserTopicDto(
        UUID id,
        UUID topicId,
        String canonicalName,
        String topicType,
        String relationType,
        String proficiency,
        String visibility
) {
}

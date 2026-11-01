package com.bookbridge.knowledge;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Full replacement of the caller's topic relations (PUT semantics). The frontend
 * chip editor owns the whole set across relation types and sends it in one call.
 * A null/empty list clears all of the user's topics.
 */
public record UserTopicsUpdateRequest(@Valid List<Item> items) {

    public record Item(
            @Size(max = 120) String topicName,
            UUID topicId,
            @Size(max = 30) String topicType,
            @NotBlank
            @Pattern(regexp = "INTERESTED_IN|LEARNING|CAN_HELP_WITH|EXPERIENCED_IN",
                    message = "relationType must be one of INTERESTED_IN, LEARNING, CAN_HELP_WITH, EXPERIENCED_IN")
            String relationType,
            @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED|EXPERT",
                    message = "proficiency must be one of BEGINNER, INTERMEDIATE, ADVANCED, EXPERT")
            String proficiency,
            @Pattern(regexp = "PRIVATE|PLATFORM_USERS|PUBLIC",
                    message = "visibility must be one of PRIVATE, PLATFORM_USERS, PUBLIC")
            String visibility
    ) {
    }
}

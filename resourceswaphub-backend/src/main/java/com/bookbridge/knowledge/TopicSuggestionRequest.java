package com.bookbridge.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A user proposal for a topic missing from the canonical taxonomy. Suggestions are
 * normalized and de-duplicated against existing topics; a genuinely new name is
 * accepted into the community taxonomy (brief Part 3 §9 "allow suggestions for
 * missing topics"). A moderated review queue is future work, not this release.
 */
public record TopicSuggestionRequest(
        @NotBlank @Size(max = 120) String name,
        @Pattern(regexp = "SKILL|SUBJECT|TECHNOLOGY|INTEREST|EXPERTISE|LEARNING_GOAL|EXAM",
                message = "topicType must be one of SKILL, SUBJECT, TECHNOLOGY, INTEREST, EXPERTISE, LEARNING_GOAL, EXAM")
        String topicType
) {
}

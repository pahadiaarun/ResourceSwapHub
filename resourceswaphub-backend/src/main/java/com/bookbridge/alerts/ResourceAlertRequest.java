package com.bookbridge.alerts;

import java.util.UUID;

import jakarta.validation.constraints.Size;

public record ResourceAlertRequest(
        @Size(max = 120, message = "Keyword must be 120 characters or fewer")
        String keyword,
        UUID categoryId,
        @Size(max = 120, message = "Subject must be 120 characters or fewer")
        String subject,
        @Size(max = 80, message = "Academic level must be 80 characters or fewer")
        String academicLevel,
        UUID stateId,
        UUID cityId,
        Boolean freeOnly,
        Boolean enabled
) {
}

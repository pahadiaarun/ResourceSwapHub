package com.bookbridge.profile;

import java.time.LocalDate;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for an employment record. Used for both POST and PATCH.
 * {@code designation} is required by the schema; the service enforces it on create
 * and leaves it unchanged on a partial PATCH when null.
 */
public record EmploymentRequest(
        @Size(max = 150) String companyName,
        @Size(max = 120) String designation,
        @Size(max = 100) String industry,
        @Pattern(regexp = "FULL_TIME|PART_TIME|INTERNSHIP|CONTRACT|FREELANCE|SELF_EMPLOYED|OTHER",
                message = "employmentType must be one of FULL_TIME, PART_TIME, INTERNSHIP, CONTRACT, FREELANCE, SELF_EMPLOYED, OTHER")
        String employmentType,
        @Size(max = 80) String workCity,
        @Size(max = 80) String workState,
        LocalDate startDate,
        LocalDate endDate,
        Boolean current,
        @Pattern(regexp = "PRIVATE|PLATFORM_USERS|PUBLIC",
                message = "visibility must be one of PRIVATE, PLATFORM_USERS, PUBLIC")
        String visibility,
        @Size(max = 2000) String description,
        @Size(max = 2000) String skillsUsed
) {
}

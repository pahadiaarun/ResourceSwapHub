package com.bookbridge.profile;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for an education record. Used for both POST (all provided
 * fields are set) and PATCH (only non-null fields are applied). At most one of
 * {@code institutionId} (canonical directory) or {@code institutionNameManual}
 * (free text) should be supplied; the service clears the other to keep official and
 * manual entries distinguishable.
 */
public record EducationRequest(
        UUID institutionId,
        @Size(max = 200) String institutionNameManual,
        @Size(max = 30) String educationLevel,
        @Size(max = 100) String degree,
        @Size(max = 120) String course,
        @Size(max = 100) String branchSpecialization,
        @Size(max = 100) String department,
        @Size(max = 30) String schoolBoard,
        @Size(max = 20) String grade,
        @Size(max = 60) String academicStream,
        @Min(1900) @Max(2200) Integer startYear,
        @Min(1900) @Max(2200) Integer endYear,
        @Size(max = 30) String currentYear,
        @Size(max = 30) String currentSemester,
        @Min(1900) @Max(2200) Integer expectedCompletionYear,
        @Size(max = 20) String academicSession,
        @Pattern(regexp = "CURRENT|COMPLETED|PAUSED|DISCONTINUED",
                message = "educationStatus must be one of CURRENT, COMPLETED, PAUSED, DISCONTINUED")
        String educationStatus,
        Boolean current,
        Boolean primary,
        @DecimalMin("0.00") @DecimalMax("999.99") BigDecimal gradePercentage,
        @Pattern(regexp = "PRIVATE|PLATFORM_USERS|PUBLIC",
                message = "visibility must be one of PRIVATE, PLATFORM_USERS, PUBLIC")
        String visibility,
        @Size(max = 2000) String description
) {
}

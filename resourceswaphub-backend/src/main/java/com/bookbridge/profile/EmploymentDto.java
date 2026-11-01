package com.bookbridge.profile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EmploymentDto(
        UUID id,
        String companyName,
        String designation,
        String industry,
        String employmentType,
        String workCity,
        String workState,
        LocalDate startDate,
        LocalDate endDate,
        boolean current,
        String visibility,
        String description,
        String skillsUsed,
        Instant createdAt,
        Instant updatedAt
) {
    public static EmploymentDto from(UserEmployment e) {
        return new EmploymentDto(
                e.getId(),
                e.getCompanyName(),
                e.getDesignation(),
                e.getIndustry(),
                e.getEmploymentType(),
                e.getWorkCity(),
                e.getWorkState(),
                e.getStartDate(),
                e.getEndDate(),
                Boolean.TRUE.equals(e.getCurrent()),
                e.getVisibility(),
                e.getDescription(),
                e.getSkillsUsed(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}

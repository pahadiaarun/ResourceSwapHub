package com.bookbridge.profile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EducationDto(
        UUID id,
        UUID institutionId,
        String institutionName,
        String institutionNameManual,
        String educationLevel,
        String degree,
        String course,
        String branchSpecialization,
        String department,
        String schoolBoard,
        String grade,
        String academicStream,
        Integer startYear,
        Integer endYear,
        String currentYear,
        String currentSemester,
        Integer expectedCompletionYear,
        String academicSession,
        String educationStatus,
        boolean current,
        boolean primary,
        BigDecimal gradePercentage,
        String visibility,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
    /** {@code resolvedInstitutionName} is the canonical directory name when institutionId is set, else null. */
    public static EducationDto from(UserEducation e, String resolvedInstitutionName) {
        return new EducationDto(
                e.getId(),
                e.getInstitutionId(),
                resolvedInstitutionName,
                e.getInstitutionNameManual(),
                e.getEducationLevel(),
                e.getDegree(),
                e.getCourse(),
                e.getBranchSpecialization(),
                e.getDepartment(),
                e.getSchoolBoard(),
                e.getGrade(),
                e.getAcademicStream(),
                e.getStartYear(),
                e.getEndYear(),
                e.getCurrentYear(),
                e.getCurrentSemester(),
                e.getExpectedCompletionYear(),
                e.getAcademicSession(),
                e.getEducationStatus(),
                Boolean.TRUE.equals(e.getCurrent()),
                Boolean.TRUE.equals(e.getPrimary()),
                e.getGradePercentage(),
                e.getVisibility(),
                e.getDescription(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}

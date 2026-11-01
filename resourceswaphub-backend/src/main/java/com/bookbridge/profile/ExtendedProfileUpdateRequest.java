package com.bookbridge.profile;

import java.util.UUID;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExtendedProfileUpdateRequest(
        @Size(max = 500) String bio,
        @Size(max = 80) String state,
        @Size(max = 80) String country,
        UUID countryId,
        UUID stateId,
        UUID cityId,
        @Size(max = 10) String preferredLanguage,
        @Pattern(regexp = "PUBLIC|PRIVATE|CONNECTIONS_ONLY",
                message = "profileVisibility must be one of PUBLIC, PRIVATE, CONNECTIONS_ONLY")
        String profileVisibility,
        @Pattern(regexp = "SCHOOL_STUDENT|COLLEGE_STUDENT|WORKING_PROFESSIONAL|TEACHER_EDUCATOR|OTHER",
                message = "userType must be one of SCHOOL_STUDENT, COLLEGE_STUDENT, WORKING_PROFESSIONAL, TEACHER_EDUCATOR, OTHER")
        String userType,
        // School student fields
        @Size(max = 200) String schoolName,
        @Size(max = 80) String schoolLocationState,
        @Size(max = 80) String schoolLocationCity,
        @Size(max = 80) String schoolLocationDistrict,
        @Size(max = 20) String schoolClass,
        @Size(max = 50) String schoolStream,
        @Pattern(regexp = "CBSE|ICSE|STATE_BOARD|IB|CAMBRIDGE|OTHER",
                message = "schoolBoard must be one of CBSE, ICSE, STATE_BOARD, IB, CAMBRIDGE, OTHER")
        String schoolBoard,
        @Size(max = 20) String schoolAcademicYear,
        Integer schoolExpectedGraduationYear,
        // College student fields
        @Size(max = 200) String collegeName,
        @Size(max = 50) String collegeInstitutionType,
        @Size(max = 80) String collegeCity,
        @Size(max = 80) String collegeState,
        @Size(max = 100) String collegeCourse,
        @Size(max = 100) String collegeBranch,
        @Size(max = 100) String collegeDepartment,
        @Size(max = 30) String collegeCurrentYearSemester,
        Integer collegeAdmissionYear,
        Integer collegeExpectedGraduationYear,
        // Professional fields
        @Size(max = 120) String professionalDesignation,
        @Size(max = 150) String professionalCompany,
        @Size(max = 100) String professionalIndustry,
        @Size(max = 150) String professionalWorkLocation,
        Integer professionalYearsExperience,
        @Size(max = 100) String professionalHighestQualification,
        @Size(max = 200) String professionalCollegeAttended,
        // Teacher fields
        @Size(max = 200) String teacherInstitutionName,
        @Size(max = 50) String teacherInstitutionType,
        @Size(max = 2000) String teacherSubjects,
        @Size(max = 2000) String teacherClassesTaught,
        Integer teacherYearsExperience,
        @Size(max = 2000) String teacherAreasExpertise,
        @Size(max = 2000) String teacherCanHelpWith,
        // Other
        @Size(max = 150) String otherOccupation,
        @Size(max = 2000) String otherLearningGoals,
        @Size(max = 2000) String otherCanContribute
) {
}

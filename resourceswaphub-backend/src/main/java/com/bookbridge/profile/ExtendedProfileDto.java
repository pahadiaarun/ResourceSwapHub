package com.bookbridge.profile;

import java.util.UUID;

import com.bookbridge.location.LocationDto;
import com.bookbridge.user.User;

public record ExtendedProfileDto(
        UUID id,
        String name,
        String email,
        String avatarUrl,
        String bio,
        String city,
        String locality,
        String state,
        String country,
        LocationDto location,
        String preferredLanguage,
        String profileVisibility,
        String userType,
        boolean profileComplete,
        boolean profileCompletedExtended,
        // School
        String schoolName,
        String schoolLocationState,
        String schoolLocationCity,
        String schoolLocationDistrict,
        String schoolClass,
        String schoolStream,
        String schoolBoard,
        String schoolAcademicYear,
        Integer schoolExpectedGraduationYear,
        // College
        String collegeName,
        String collegeInstitutionType,
        String collegeCity,
        String collegeState,
        String collegeCourse,
        String collegeBranch,
        String collegeDepartment,
        String collegeCurrentYearSemester,
        Integer collegeAdmissionYear,
        Integer collegeExpectedGraduationYear,
        // Professional
        String professionalDesignation,
        String professionalCompany,
        String professionalIndustry,
        String professionalWorkLocation,
        Integer professionalYearsExperience,
        String professionalHighestQualification,
        String professionalCollegeAttended,
        // Teacher
        String teacherInstitutionName,
        String teacherInstitutionType,
        String teacherSubjects,
        String teacherClassesTaught,
        Integer teacherYearsExperience,
        String teacherAreasExpertise,
        String teacherCanHelpWith,
        // Other
        String otherOccupation,
        String otherLearningGoals,
        String otherCanContribute
) {
    public static ExtendedProfileDto from(User user) {
        return new ExtendedProfileDto(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getCity(),
                user.getLocality(),
                user.getState(),
                user.getCountry(),
                new LocationDto(user.getCountryId(), user.getStateId(), user.getCityId(),
                        user.getCountry(), user.getState(), user.getCity()),
                user.getPreferredLanguage(),
                user.getProfileVisibility(),
                user.getUserType(),
                user.isProfileComplete(),
                Boolean.TRUE.equals(user.getProfileCompletedExtended()),
                user.getSchoolName(),
                user.getSchoolLocationState(),
                user.getSchoolLocationCity(),
                user.getSchoolLocationDistrict(),
                user.getSchoolClass(),
                user.getSchoolStream(),
                user.getSchoolBoard(),
                user.getSchoolAcademicYear(),
                user.getSchoolExpectedGraduationYear(),
                user.getCollegeName(),
                user.getCollegeInstitutionType(),
                user.getCollegeCity(),
                user.getCollegeState(),
                user.getCollegeCourse(),
                user.getCollegeBranch(),
                user.getCollegeDepartment(),
                user.getCollegeCurrentYearSemester(),
                user.getCollegeAdmissionYear(),
                user.getCollegeExpectedGraduationYear(),
                user.getProfessionalDesignation(),
                user.getProfessionalCompany(),
                user.getProfessionalIndustry(),
                user.getProfessionalWorkLocation(),
                user.getProfessionalYearsExperience(),
                user.getProfessionalHighestQualification(),
                user.getProfessionalCollegeAttended(),
                user.getTeacherInstitutionName(),
                user.getTeacherInstitutionType(),
                user.getTeacherSubjects(),
                user.getTeacherClassesTaught(),
                user.getTeacherYearsExperience(),
                user.getTeacherAreasExpertise(),
                user.getTeacherCanHelpWith(),
                user.getOtherOccupation(),
                user.getOtherLearningGoals(),
                user.getOtherCanContribute()
        );
    }

    /**
     * The only view of a profile exposed to other users: a minimal public identity card.
     * Email, location, and every role-specific wide column are withheld — they have no
     * per-field visibility and must never be auto-displayed (brief Part 10 §23). Sharing
     * of education/employment/topics happens through their own visibility-gated endpoints.
     */
    public static ExtendedProfileDto publicView(User user) {
        return new ExtendedProfileDto(
                user.getId(),                 // id
                user.getDisplayName(),        // name
                null,                         // email — never exposed to others
                user.getAvatarUrl(),          // avatarUrl
                null,                         // bio
                null,                         // city
                null,                         // locality
                null,                         // state
                null,                         // country
                null,                         // location
                null,                         // preferredLanguage
                null,                         // profileVisibility
                null,                         // userType
                false,                        // profileComplete
                false,                        // profileCompletedExtended
                // school (9)
                null, null, null, null, null, null, null, null, null,
                // college (10)
                null, null, null, null, null, null, null, null, null, null,
                // professional (7)
                null, null, null, null, null, null, null,
                // teacher (7)
                null, null, null, null, null, null, null,
                // other (3)
                null, null, null
        );
    }
}

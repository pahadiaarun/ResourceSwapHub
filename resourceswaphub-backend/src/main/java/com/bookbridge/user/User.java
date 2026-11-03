package com.bookbridge.user;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "google_subject", length = 255)
    private String googleSubject;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(length = 80)
    private String city;

    @Column(length = 120)
    private String locality;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(length = 500)
    private String bio;

    @Column(name = "country_id")
    private UUID countryId;

    @Column(name = "state_id")
    private UUID stateId;

    @Column(name = "city_id")
    private UUID cityId;

    @Column(length = 80)
    private String state;

    @Column(length = 80)
    private String country = "India";

    @Column(name = "preferred_language", length = 10)
    private String preferredLanguage = "en";

    @Column(nullable = false, length = 20)
    private String role = "USER";

    @Column(name = "profile_visibility", length = 20)
    private String profileVisibility = "PUBLIC";

    @Column(name = "user_type", length = 30)
    private String userType;

    @Column(name = "profile_completed_extended")
    private Boolean profileCompletedExtended = false;

    // School student fields
    @Column(name = "school_name", length = 200)
    private String schoolName;

    @Column(name = "school_location_state", length = 80)
    private String schoolLocationState;

    @Column(name = "school_location_city", length = 80)
    private String schoolLocationCity;

    @Column(name = "school_location_district", length = 80)
    private String schoolLocationDistrict;

    @Column(name = "school_class", length = 20)
    private String schoolClass;

    @Column(name = "school_stream", length = 50)
    private String schoolStream;

    @Column(name = "school_board", length = 30)
    private String schoolBoard;

    @Column(name = "school_academic_year", length = 20)
    private String schoolAcademicYear;

    @Column(name = "school_expected_graduation_year")
    private Integer schoolExpectedGraduationYear;

    // College student fields
    @Column(name = "college_name", length = 200)
    private String collegeName;

    @Column(name = "college_institution_type", length = 50)
    private String collegeInstitutionType;

    @Column(name = "college_city", length = 80)
    private String collegeCity;

    @Column(name = "college_state", length = 80)
    private String collegeState;

    @Column(name = "college_course", length = 100)
    private String collegeCourse;

    @Column(name = "college_branch", length = 100)
    private String collegeBranch;

    @Column(name = "college_department", length = 100)
    private String collegeDepartment;

    @Column(name = "college_current_year_semester", length = 30)
    private String collegeCurrentYearSemester;

    @Column(name = "college_admission_year")
    private Integer collegeAdmissionYear;

    @Column(name = "college_expected_graduation_year")
    private Integer collegeExpectedGraduationYear;

    // Professional fields
    @Column(name = "professional_designation", length = 120)
    private String professionalDesignation;

    @Column(name = "professional_company", length = 150)
    private String professionalCompany;

    @Column(name = "professional_industry", length = 100)
    private String professionalIndustry;

    @Column(name = "professional_work_location", length = 150)
    private String professionalWorkLocation;

    @Column(name = "professional_years_experience")
    private Integer professionalYearsExperience;

    @Column(name = "professional_highest_qualification", length = 100)
    private String professionalHighestQualification;

    @Column(name = "professional_college_attended", length = 200)
    private String professionalCollegeAttended;

    // Teacher fields
    @Column(name = "teacher_institution_name", length = 200)
    private String teacherInstitutionName;

    @Column(name = "teacher_institution_type", length = 50)
    private String teacherInstitutionType;

    @Column(name = "teacher_subjects")
    private String teacherSubjects;

    @Column(name = "teacher_classes_taught")
    private String teacherClassesTaught;

    @Column(name = "teacher_years_experience")
    private Integer teacherYearsExperience;

    @Column(name = "teacher_areas_expertise")
    private String teacherAreasExpertise;

    @Column(name = "teacher_can_help_with")
    private String teacherCanHelpWith;

    // Other
    @Column(name = "other_occupation", length = 150)
    private String otherOccupation;

    @Column(name = "other_learning_goals")
    private String otherLearningGoals;

    @Column(name = "other_can_contribute")
    private String otherCanContribute;

    public boolean isProfileComplete() {
        return city != null && !city.isBlank() && locality != null && !locality.isBlank();
    }
}

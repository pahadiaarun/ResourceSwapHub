package com.bookbridge.profile;

import java.math.BigDecimal;
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

/**
 * One education record. A user may have many (brief Part 6 §14): current, completed,
 * paused or discontinued. The institution is either a canonical directory reference
 * ({@code institutionId}) or a free-text manual entry ({@code institutionNameManual}),
 * never both required, so official and user-entered entries stay distinguishable.
 */
@Entity
@Table(name = "user_education")
@Getter
@Setter
@NoArgsConstructor
public class UserEducation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "institution_id")
    private UUID institutionId;

    @Column(name = "institution_name_manual", length = 200)
    private String institutionNameManual;

    @Column(name = "education_level", length = 30)
    private String educationLevel;

    @Column(length = 100)
    private String degree;

    @Column(length = 120)
    private String course;

    @Column(name = "branch_specialization", length = 100)
    private String branchSpecialization;

    @Column(length = 100)
    private String department;

    @Column(name = "school_board", length = 30)
    private String schoolBoard;

    @Column(length = 20)
    private String grade;

    @Column(name = "academic_stream", length = 60)
    private String academicStream;

    @Column(name = "start_year")
    private Integer startYear;

    @Column(name = "end_year")
    private Integer endYear;

    @Column(name = "current_year", length = 30)
    private String currentYear;

    @Column(name = "current_semester", length = 30)
    private String currentSemester;

    @Column(name = "expected_completion_year")
    private Integer expectedCompletionYear;

    @Column(name = "academic_session", length = 20)
    private String academicSession;

    @Column(name = "education_status", nullable = false, length = 20)
    private String educationStatus = "CURRENT";

    @Column(name = "current")
    private Boolean current = false;

    @Column(name = "is_primary", nullable = false)
    private Boolean primary = false;

    @Column(name = "grade_percentage", precision = 5, scale = 2)
    private BigDecimal gradePercentage;

    @Column(name = "visibility", nullable = false, length = 20)
    private String visibility = "PRIVATE";

    @Column(name = "description")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

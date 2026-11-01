package com.bookbridge.profile;

import java.time.Instant;
import java.time.LocalDate;
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
 * One employment record. A user may have many (brief Part 6 §15) — internships, past
 * employers, current employer. Company name is free text in this release; no company
 * directory is required.
 */
@Entity
@Table(name = "user_employment")
@Getter
@Setter
@NoArgsConstructor
public class UserEmployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "company_name", length = 150)
    private String companyName;

    @Column(name = "designation", nullable = false, length = 120)
    private String designation;

    @Column(length = 100)
    private String industry;

    @Column(name = "employment_type", length = 30)
    private String employmentType;

    @Column(name = "work_city", length = 80)
    private String workCity;

    @Column(name = "work_state", length = 80)
    private String workState;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "current")
    private Boolean current = false;

    @Column(name = "visibility", nullable = false, length = 20)
    private String visibility = "PRIVATE";

    @Column(name = "description")
    private String description;

    @Column(name = "skills_used")
    private String skillsUsed;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

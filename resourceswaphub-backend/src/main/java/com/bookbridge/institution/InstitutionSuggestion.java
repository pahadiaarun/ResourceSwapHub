package com.bookbridge.institution;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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
@Table(name = "institution_suggestions")
@Getter
@Setter
@NoArgsConstructor
public class InstitutionSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "suggested_by")
    private UUID suggestedBy;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(name = "institution_type", nullable = false, length = 50)
    private String institutionType;

    @Column(length = 80)
    private String state;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String district;

    @Column(name = "additional_info")
    private String additionalInfo;

    @Column(length = 20)
    private String status = "PENDING";

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_notes")
    private String reviewNotes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

package com.bookbridge.institution;

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
@Table(name = "institutions")
@Getter
@Setter
@NoArgsConstructor
public class Institution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 300)
    private String normalizedName;

    @Column(name = "institution_type", nullable = false, length = 50)
    private String institutionType;

    @Column(name = "official_code", length = 100)
    private String officialCode;

    @Column(name = "official_code_type", length = 30)
    private String officialCodeType;

    @Column(length = 80)
    private String state;

    @Column(length = 80)
    private String district;

    @Column(length = 80)
    private String city;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "source_last_updated")
    private Instant sourceLastUpdated;

    @Column(name = "verification_status", length = 20)
    private String verificationStatus = "PENDING";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

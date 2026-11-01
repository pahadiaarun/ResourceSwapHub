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

/**
 * An alternative name for an institution (abbreviation, former name, alternate spelling,
 * common name) so search can resolve "DTU" to "Delhi Technological University".
 */
@Entity
@Table(name = "institution_aliases")
@Getter
@Setter
@NoArgsConstructor
public class InstitutionAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "alias_name", nullable = false, length = 300)
    private String aliasName;

    @Column(name = "normalized_alias", nullable = false, length = 300)
    private String normalizedAlias;

    @Column(name = "alias_type", nullable = false, length = 20)
    private String aliasType = "COMMON";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

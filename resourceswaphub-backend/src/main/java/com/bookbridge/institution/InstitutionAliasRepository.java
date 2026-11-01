package com.bookbridge.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionAliasRepository extends JpaRepository<InstitutionAlias, UUID> {

    List<InstitutionAlias> findByInstitutionId(UUID institutionId);

    Optional<InstitutionAlias> findByInstitutionIdAndNormalizedAlias(UUID institutionId, String normalizedAlias);
}

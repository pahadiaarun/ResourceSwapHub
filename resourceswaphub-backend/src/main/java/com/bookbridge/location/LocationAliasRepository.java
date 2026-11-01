package com.bookbridge.location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationAliasRepository extends JpaRepository<LocationAlias, UUID> {

    List<LocationAlias> findByEntityTypeAndEntityIdOrderByIdAsc(String entityType, UUID entityId);

    Optional<LocationAlias> findFirstByEntityTypeAndNormalizedAlias(String entityType, String normalizedAlias);

    List<LocationAlias> findByEntityTypeAndNormalizedAlias(String entityType, String normalizedAlias);

    @Query("select a.entityId from LocationAlias a where a.entityType = :entityType "
            + "and a.normalizedAlias = :normalizedAlias")
    List<UUID> findEntityIdsByAlias(@Param("entityType") String entityType,
                                    @Param("normalizedAlias") String normalizedAlias);
}

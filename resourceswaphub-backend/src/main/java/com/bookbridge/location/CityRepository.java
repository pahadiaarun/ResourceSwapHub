package com.bookbridge.location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CityRepository extends JpaRepository<City, UUID> {

    List<City> findByStateIdAndActiveTrueOrderByNormalizedNameAsc(UUID stateId);

    Optional<City> findFirstByStateIdAndNormalizedName(UUID stateId, String normalizedName);

    Optional<City> findFirstByStateIdAndOfficialCode(UUID stateId, String officialCode);

    Optional<City> findFirstByStateIdAndName(UUID stateId, String name);

    Optional<City> findFirstByNormalizedName(String normalizedName);

    /** Every active city sharing a normalized name, across all states — used to prove uniqueness. */
    List<City> findByNormalizedNameAndActiveTrue(String normalizedName);

    /**
     * Alias-aware, trigram-ranked city search scoped to one state, mirroring
     * {@code InstitutionRepository#searchRanked}. Results are always within {@code stateId} so a
     * city name that exists in two states can never leak across. CASTs keep null optional
     * filters typed. pg_trgm and the trigram indexes are created in V5/V8.
     */
    @Query(value = """
            SELECT c.* FROM cities c
            LEFT JOIN location_aliases a
              ON a.entity_type = 'CITY' AND a.entity_id = c.id
            WHERE c.is_active = TRUE
              AND c.state_id = CAST(:stateId AS uuid)
              AND (
                :q = ''
                OR c.normalized_name LIKE CONCAT('%', :q, '%')
                OR a.normalized_alias LIKE CONCAT('%', :q, '%')
                OR similarity(c.normalized_name, :q) > 0.15
              )
            GROUP BY c.id
            ORDER BY
              bool_or(c.normalized_name = :q) DESC,
              bool_or(a.normalized_alias = :q) DESC,
              bool_or(c.normalized_name LIKE CONCAT(:q, '%')) DESC,
              max(similarity(c.normalized_name, :q)) DESC,
              c.name ASC
            """, nativeQuery = true)
    List<City> searchInState(@Param("stateId") UUID stateId,
                             @Param("q") String q,
                             Pageable pageable);

    @Query(value = """
            SELECT COUNT(DISTINCT c.id) FROM cities c
            LEFT JOIN location_aliases a
              ON a.entity_type = 'CITY' AND a.entity_id = c.id
            WHERE c.is_active = TRUE
              AND c.state_id = CAST(:stateId AS uuid)
              AND (
                :q = ''
                OR c.normalized_name LIKE CONCAT('%', :q, '%')
                OR a.normalized_alias LIKE CONCAT('%', :q, '%')
                OR similarity(c.normalized_name, :q) > 0.15
              )
            """, nativeQuery = true)
    long countSearchInState(@Param("stateId") UUID stateId, @Param("q") String q);
}

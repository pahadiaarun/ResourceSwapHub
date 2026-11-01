package com.bookbridge.institution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstitutionRepository extends JpaRepository<Institution, UUID> {

    Optional<Institution> findByOfficialCodeTypeAndOfficialCode(String officialCodeType, String officialCode);

    Optional<Institution> findFirstByNormalizedNameAndState(String normalizedName, String state);

    Optional<Institution> findFirstByNormalizedName(String normalizedName);

    /**
     * Alias-aware, trigram-ranked search. Ranking follows brief Part 8 §18:
     * exact canonical name > exact alias > exact official code > prefix > partial > fuzzy.
     * CASTs keep the optional filters typed when null (a bare ":p IS NULL" leaves the
     * parameter untyped and PostgreSQL resolves it against bytea). pg_trgm is enabled in V5;
     * the normalized-name trigram index is added in V7.
     */
    @Query(value = """
            SELECT i.* FROM institutions i
            LEFT JOIN institution_aliases a ON a.institution_id = i.id
            WHERE
              (CAST(:type AS text) IS NULL OR i.institution_type = CAST(:type AS text))
              AND (CAST(:state AS text) IS NULL OR lower(i.state) = lower(CAST(:state AS text)))
              AND (CAST(:city AS text) IS NULL OR lower(i.city) = lower(CAST(:city AS text)))
              AND (
                :q = ''
                OR i.normalized_name LIKE CONCAT('%', :q, '%')
                OR a.normalized_alias LIKE CONCAT('%', :q, '%')
                OR lower(COALESCE(i.official_code, '')) LIKE CONCAT('%', lower(:q), '%')
                OR similarity(i.normalized_name, :q) > 0.1
              )
            GROUP BY i.id
            ORDER BY
              bool_or(i.normalized_name = :q) DESC,
              bool_or(a.normalized_alias = :q) DESC,
              bool_or(lower(COALESCE(i.official_code, '')) = lower(:q)) DESC,
              bool_or(i.normalized_name LIKE CONCAT(:q, '%')) DESC,
              bool_or(i.normalized_name LIKE CONCAT('%', :q, '%')) DESC,
              max(similarity(i.normalized_name, :q)) DESC,
              i.name ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Institution> searchRanked(@Param("q") String q,
                                   @Param("type") String type,
                                   @Param("state") String state,
                                   @Param("city") String city,
                                   @Param("limit") int limit,
                                   @Param("offset") int offset);

    @Query(value = """
            SELECT COUNT(DISTINCT i.id) FROM institutions i
            LEFT JOIN institution_aliases a ON a.institution_id = i.id
            WHERE
              (CAST(:type AS text) IS NULL OR i.institution_type = CAST(:type AS text))
              AND (CAST(:state AS text) IS NULL OR lower(i.state) = lower(CAST(:state AS text)))
              AND (CAST(:city AS text) IS NULL OR lower(i.city) = lower(CAST(:city AS text)))
              AND (
                :q = ''
                OR i.normalized_name LIKE CONCAT('%', :q, '%')
                OR a.normalized_alias LIKE CONCAT('%', :q, '%')
                OR lower(COALESCE(i.official_code, '')) LIKE CONCAT('%', lower(:q), '%')
                OR similarity(i.normalized_name, :q) > 0.1
              )
            """, nativeQuery = true)
    long countSearch(@Param("q") String q,
                     @Param("type") String type,
                     @Param("state") String state,
                     @Param("city") String city);

    @Query(value = "SELECT DISTINCT state FROM institutions WHERE state IS NOT NULL AND state <> '' ORDER BY state",
            nativeQuery = true)
    List<String> distinctStates();

    @Query(value = "SELECT DISTINCT institution_type FROM institutions ORDER BY institution_type", nativeQuery = true)
    List<String> distinctTypes();
}

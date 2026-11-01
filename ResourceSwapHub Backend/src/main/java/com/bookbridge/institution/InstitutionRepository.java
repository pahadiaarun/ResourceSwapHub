package com.bookbridge.institution;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstitutionRepository extends JpaRepository<Institution, UUID> {

    @Query("SELECT i FROM Institution i WHERE " +
           "LOWER(i.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(i.normalizedName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(COALESCE(i.officialCode, '')) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Institution> search(@Param("q") String q, Pageable pageable);

    // The filters are cast so PostgreSQL receives typed nulls; a bare ":p IS NULL OR LOWER(:p) = ..."
    // leaves the null untyped and PostgreSQL resolves LOWER(?) against bytea.
    @Query("SELECT i FROM Institution i WHERE " +
           "(CAST(:type AS String) IS NULL OR i.institutionType = CAST(:type AS String)) AND " +
           "(CAST(:state AS String) IS NULL OR LOWER(i.state) = LOWER(CAST(:state AS String))) AND " +
           "(CAST(:city AS String) IS NULL OR LOWER(i.city) = LOWER(CAST(:city AS String))) AND " +
           "(LOWER(i.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(i.normalizedName) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Institution> searchWithFilters(@Param("q") String q,
                                       @Param("type") String type,
                                       @Param("state") String state,
                                       @Param("city") String city,
                                       Pageable pageable);
}

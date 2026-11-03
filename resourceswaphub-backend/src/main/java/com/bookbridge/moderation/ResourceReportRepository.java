package com.bookbridge.moderation;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceReportRepository extends JpaRepository<ResourceReport, UUID> {

    boolean existsByListingIdAndReporterIdAndStatus(UUID listingId, UUID reporterId, ReportStatus status);

    @EntityGraph(attributePaths = {"listing", "listing.book", "reporter"})
    @Query("select r from ResourceReport r where (:status is null or r.status = :status) "
            + "order by r.createdAt desc")
    Page<ResourceReport> pageByStatus(@Param("status") ReportStatus status, Pageable pageable);

    Optional<ResourceReport> findById(UUID id);
}

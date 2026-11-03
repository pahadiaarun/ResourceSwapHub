package com.bookbridge.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceRequestRepository extends JpaRepository<ResourceRequest, UUID> {

    Page<ResourceRequest> findByRequesterIdOrderByCreatedAtDesc(UUID requesterId, Pageable pageable);

    long countByRequesterIdAndCreatedAtAfter(UUID requesterId, java.time.Instant after);

    Page<ResourceRequest> findByRequesterIdAndStatusOrderByCreatedAtDesc(
            UUID requesterId, RequestStatus status, Pageable pageable);

    /** Lazy expiry: anything due is closed by the database, so expiry works even without the scheduler. */
    @Modifying
    @Query("update ResourceRequest r set r.status = com.bookbridge.request.RequestStatus.EXPIRED, "
            + "r.expiredAt = CURRENT_TIMESTAMP "
            + "where r.status = com.bookbridge.request.RequestStatus.OPEN and r.expiresAt < :now")
    int expireDueRequests(@Param("now") Instant now);

    /**
     * Server-side browsing of open requests (or, when includeStatus is set, any status —
     * used by the owner). Same shape as the listing discovery so both stay consistent.
     */
    @Query(value = """
            SELECT r.* FROM resource_requests r
            WHERE (
                :includeStatus = true
                OR (r.status = 'OPEN' AND r.expires_at >= CAST(:now AS timestamptz))
              )
              AND (CAST(:status AS text) IS NULL OR r.status = CAST(:status AS text))
              AND (
                :hasQuery = false
                OR r.search_vector @@ to_tsquery('simple', CAST(:tsQuery AS text))
                OR lower(r.title) LIKE CAST(:likeQuery AS text)
                OR lower(coalesce(r.description, '')) LIKE CAST(:likeQuery AS text)
                OR lower(coalesce(r.subject, '')) LIKE CAST(:likeQuery AS text)
                OR lower(coalesce(r.author, '')) LIKE CAST(:likeQuery AS text)
              )
              AND (CAST(:categoryId AS uuid) IS NULL OR r.category_id = CAST(:categoryId AS uuid))
              AND (CAST(:subject AS text) IS NULL
                   OR lower(r.subject) = lower(CAST(:subject AS text)))
              AND (CAST(:academicLevel AS text) IS NULL
                   OR r.academic_level = CAST(:academicLevel AS text))
              AND (CAST(:freeOnly AS boolean) IS NULL OR r.free_only = CAST(:freeOnly AS boolean))
              AND (CAST(:minPrice AS numeric) IS NULL OR r.price_min >= CAST(:minPrice AS numeric))
              AND (CAST(:maxPrice AS numeric) IS NULL
                   OR (r.price_max IS NOT NULL AND r.price_max <= CAST(:maxPrice AS numeric)))
              AND (CAST(:stateId AS uuid) IS NULL OR r.state_id = CAST(:stateId AS uuid))
              AND (CAST(:cityId AS uuid) IS NULL OR r.city_id = CAST(:cityId AS uuid))
              AND (CAST(:condition AS text) IS NULL OR r.preferred_condition = CAST(:condition AS text))
              AND (CAST(:requesterId AS uuid) IS NULL OR r.requester_id = CAST(:requesterId AS uuid))
            ORDER BY r.created_at DESC, r.id DESC
            """, nativeQuery = true)
    Page<ResourceRequest> browse(
            @Param("includeStatus") boolean includeStatus,
            @Param("now") Instant now,
            @Param("status") String status,
            @Param("hasQuery") boolean hasQuery,
            @Param("tsQuery") String tsQuery,
            @Param("likeQuery") String likeQuery,
            @Param("categoryId") UUID categoryId,
            @Param("subject") String subject,
            @Param("academicLevel") String academicLevel,
            @Param("freeOnly") Boolean freeOnly,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("stateId") UUID stateId,
            @Param("cityId") UUID cityId,
            @Param("condition") String condition,
            @Param("requesterId") UUID requesterId,
            Pageable pageable
    );

    @Modifying
    @Query("update ResourceRequest r set r.status = com.bookbridge.request.RequestStatus.FULFILLED, "
            + "r.fulfilledAt = CURRENT_TIMESTAMP where r.id = :id and r.status = com.bookbridge.request.RequestStatus.OPEN")
    int fulfillIfOpen(@Param("id") UUID id);

    @Modifying
    @Query("update ResourceRequest r set r.status = com.bookbridge.request.RequestStatus.CANCELLED, "
            + "r.cancelledAt = CURRENT_TIMESTAMP where r.id = :id and r.status = com.bookbridge.request.RequestStatus.OPEN")
    int cancelIfOpen(@Param("id") UUID id);

    List<ResourceRequest> findByRequesterIdAndStatus(UUID requesterId, RequestStatus status);
}

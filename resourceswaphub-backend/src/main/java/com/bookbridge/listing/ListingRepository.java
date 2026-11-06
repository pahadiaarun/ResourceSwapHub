package com.bookbridge.listing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

    @EntityGraph(attributePaths = {"book", "owner", "category", "images", "images.media", "digitalMedia"})
    @Query("select l from Listing l where l.id = :id")
    Optional<Listing> findWithDetailsById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Listing l where l.id = :id")
    Optional<Listing> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"book", "category", "images", "images.media"})
    @Query("select l from Listing l where l.id in :ids")
    List<Listing> findWithDetailsByIdIn(@Param("ids") List<UUID> ids);

    Page<Listing> findByOwnerId(UUID ownerId, Pageable pageable);

    Page<Listing> findByOwnerIdAndStatus(UUID ownerId, ListingStatus status, Pageable pageable);

    Optional<Listing> findByOwnerIdAndClientRef(UUID ownerId, String clientRef);

    long countByOwnerIdAndCreatedAtAfter(UUID ownerId, Instant after);

    /**
     * Rows the directory has not placed yet, plus rows whose city text no longer matches the
     * directory record stored on them, walked with an id cursor. The directory entities are
     * referenced by name only, so this stays a read of ids rather than a graph of associations.
     */
    @Query("select l.id from Listing l where l.id > :after and ("
            + "(l.cityId is null and l.city is not null) "
            + "or (l.cityId is not null and l.city <> (select c.name from City c where c.id = l.cityId))) "
            + "order by l.id")
    List<UUID> findLocationCandidateIdsAfter(@Param("after") UUID after, Pageable pageable);

    @Query("select distinct l.city from Listing l where l.status = 'AVAILABLE' order by l.city asc")
    List<String> findDistinctAvailableCities();

    /**
     * Server-side discovery across both catalogue-anchored books and free-form resource
     * listings. Only AVAILABLE, un-moderated rows are returned.
     *
     * sortCode: 1 = oldest first, 2 = price ascending, 3 = price descending, 0/other = newest first.
     */
    @Query(value = """
            SELECT l.* FROM listings l
            LEFT JOIN books b ON b.id = l.book_id
            WHERE l.status = 'AVAILABLE'
              AND l.moderation_state = 'NONE'
              AND (
                :hasQuery = false
                OR b.search_vector @@ to_tsquery('simple', CAST(:tsQuery AS text))
                OR l.search_vector @@ to_tsquery('simple', CAST(:tsQuery AS text))
                OR lower(coalesce(l.title, b.title)) LIKE CAST(:likeQuery AS text)
                OR lower(coalesce(l.author, '')) LIKE CAST(:likeQuery AS text)
                OR lower(coalesce(b.author, '')) LIKE CAST(:likeQuery AS text)
                OR lower(coalesce(l.subject, b.subject, '')) LIKE CAST(:likeQuery AS text)
              )
              AND (CAST(:categoryId AS uuid) IS NULL OR l.category_id = CAST(:categoryId AS uuid))
              AND (CAST(:subject AS text) IS NULL
                   OR lower(coalesce(b.subject, l.subject)) = lower(CAST(:subject AS text)))
              AND (CAST(:academicLevel AS text) IS NULL
                   OR l.academic_level = CAST(:academicLevel AS text))
              AND (CAST(:classLevel AS smallint) IS NULL OR b.class_level = CAST(:classLevel AS smallint))
              AND (CAST(:board AS text) IS NULL
                   OR coalesce(b.education_board, l.board) = CAST(:board AS text))
              AND (CAST(:minPrice AS numeric) IS NULL OR l.price >= CAST(:minPrice AS numeric))
              AND (CAST(:maxPrice AS numeric) IS NULL OR l.price <= CAST(:maxPrice AS numeric))
              AND (CAST(:priceType AS text) IS NULL OR l.price_type = CAST(:priceType AS text))
              AND (CAST(:condition AS text) IS NULL OR l.condition = CAST(:condition AS text))
              AND (CAST(:city AS text) IS NULL OR lower(l.city) = lower(CAST(:city AS text)))
              AND (CAST(:stateId AS uuid) IS NULL OR l.state_id = CAST(:stateId AS uuid))
              AND (CAST(:cityId AS uuid) IS NULL OR l.city_id = CAST(:cityId AS uuid))
              AND (CAST(:districtId AS uuid) IS NULL OR EXISTS (
                       SELECT 1 FROM cities dc WHERE dc.id = l.city_id
                       AND dc.district_id = CAST(:districtId AS uuid)))
              AND (CAST(:locality AS text) IS NULL
                   OR lower(coalesce(l.locality, '')) = lower(CAST(:locality AS text)))
            ORDER BY
              CASE WHEN :sortCode = 1 THEN l.created_at END ASC NULLS LAST,
              CASE WHEN :sortCode = 2 THEN l.price END ASC NULLS FIRST,
              CASE WHEN :sortCode = 3 THEN l.price END DESC NULLS LAST,
              l.created_at DESC, l.id DESC
            """, nativeQuery = true)
    Page<Listing> discover(
            @Param("hasQuery") boolean hasQuery,
            @Param("tsQuery") String tsQuery,
            @Param("likeQuery") String likeQuery,
            @Param("categoryId") UUID categoryId,
            @Param("subject") String subject,
            @Param("academicLevel") String academicLevel,
            @Param("classLevel") Integer classLevel,
            @Param("board") String board,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("priceType") String priceType,
            @Param("condition") String condition,
            @Param("city") String city,
            @Param("stateId") UUID stateId,
            @Param("cityId") UUID cityId,
            @Param("districtId") UUID districtId,
            @Param("locality") String locality,
            @Param("sortCode") int sortCode,
            Pageable pageable
    );
}

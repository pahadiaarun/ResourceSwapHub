package com.bookbridge.listing;

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

    @EntityGraph(attributePaths = {"book", "owner", "collection", "images", "images.media"})
    @Query("select l from Listing l where l.id = :id")
    Optional<Listing> findWithDetailsById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Listing l where l.id = :id")
    Optional<Listing> findByIdForUpdate(@Param("id") UUID id);

    Page<Listing> findByOwnerId(UUID ownerId, Pageable pageable);

    Page<Listing> findByOwnerIdAndStatus(UUID ownerId, ListingStatus status, Pageable pageable);

    List<Listing> findByCollectionIdOrderByCreatedAtDesc(UUID collectionId);

    long countByCollectionId(UUID collectionId);

    long countByCollectionIdAndStatus(UUID collectionId, ListingStatus status);

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

    @Query(value = """
            SELECT l.* FROM listings l
            JOIN books b ON b.id = l.book_id
            WHERE l.status = 'AVAILABLE'
              AND (
                :hasQuery = false
                OR b.search_vector @@ to_tsquery('simple', CAST(:tsQuery AS text))
                OR lower(b.title) LIKE CAST(:likeQuery AS text)
                OR lower(b.author) LIKE CAST(:likeQuery AS text)
                OR lower(b.subject) LIKE CAST(:likeQuery AS text)
              )
              AND (CAST(:subject AS text) IS NULL OR lower(b.subject) = lower(CAST(:subject AS text)))
              AND (CAST(:classLevel AS smallint) IS NULL OR b.class_level = CAST(:classLevel AS smallint))
              AND (CAST(:board AS text) IS NULL OR b.education_board = CAST(:board AS text))
              AND (CAST(:priceType AS text) IS NULL OR l.price_type = CAST(:priceType AS text))
              AND (CAST(:condition AS text) IS NULL OR l.condition = CAST(:condition AS text))
              AND (CAST(:city AS text) IS NULL OR lower(l.city) = lower(CAST(:city AS text)))
              AND (CAST(:stateId AS uuid) IS NULL OR l.state_id = CAST(:stateId AS uuid))
              AND (CAST(:cityId AS uuid) IS NULL OR l.city_id = CAST(:cityId AS uuid))
              AND (CAST(:districtId AS uuid) IS NULL OR EXISTS (
                       SELECT 1 FROM cities dc WHERE dc.id = l.city_id AND dc.district_id = CAST(:districtId AS uuid)))
              AND (CAST(:locality AS text) IS NULL OR lower(coalesce(l.locality, '')) = lower(CAST(:locality AS text)))
            ORDER BY l.created_at DESC, l.id DESC
            """, countQuery = """
            SELECT count(*) FROM listings l
            JOIN books b ON b.id = l.book_id
            WHERE l.status = 'AVAILABLE'
              AND (
                :hasQuery = false
                OR b.search_vector @@ to_tsquery('simple', CAST(:tsQuery AS text))
                OR lower(b.title) LIKE CAST(:likeQuery AS text)
                OR lower(b.author) LIKE CAST(:likeQuery AS text)
                OR lower(b.subject) LIKE CAST(:likeQuery AS text)
              )
              AND (CAST(:subject AS text) IS NULL OR lower(b.subject) = lower(CAST(:subject AS text)))
              AND (CAST(:classLevel AS smallint) IS NULL OR b.class_level = CAST(:classLevel AS smallint))
              AND (CAST(:board AS text) IS NULL OR b.education_board = CAST(:board AS text))
              AND (CAST(:priceType AS text) IS NULL OR l.price_type = CAST(:priceType AS text))
              AND (CAST(:condition AS text) IS NULL OR l.condition = CAST(:condition AS text))
              AND (CAST(:city AS text) IS NULL OR lower(l.city) = lower(CAST(:city AS text)))
              AND (CAST(:stateId AS uuid) IS NULL OR l.state_id = CAST(:stateId AS uuid))
              AND (CAST(:cityId AS uuid) IS NULL OR l.city_id = CAST(:cityId AS uuid))
              AND (CAST(:districtId AS uuid) IS NULL OR EXISTS (
                       SELECT 1 FROM cities dc WHERE dc.id = l.city_id AND dc.district_id = CAST(:districtId AS uuid)))
              AND (CAST(:locality AS text) IS NULL OR lower(coalesce(l.locality, '')) = lower(CAST(:locality AS text)))
            """, nativeQuery = true)
    Page<Listing> discover(
            @Param("hasQuery") boolean hasQuery,
            @Param("tsQuery") String tsQuery,
            @Param("likeQuery") String likeQuery,
            @Param("subject") String subject,
            @Param("classLevel") Integer classLevel,
            @Param("board") String board,
            @Param("priceType") String priceType,
            @Param("condition") String condition,
            @Param("city") String city,
            @Param("stateId") UUID stateId,
            @Param("cityId") UUID cityId,
            @Param("districtId") UUID districtId,
            @Param("locality") String locality,
            Pageable pageable
    );
}

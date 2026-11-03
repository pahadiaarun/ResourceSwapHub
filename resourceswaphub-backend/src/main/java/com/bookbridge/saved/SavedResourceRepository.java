package com.bookbridge.saved;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedResourceRepository extends JpaRepository<SavedResource, SavedKey> {

    @EntityGraph(attributePaths = {"listing", "listing.book", "listing.category"})
    @Query("select s from SavedResource s where s.user.id = :userId order by s.createdAt desc")
    Page<SavedResource> findForUser(@Param("userId") UUID userId, Pageable pageable);

    Optional<SavedResource> findByUserIdAndListingId(UUID userId, UUID listingId);

    @Query("select s.user.id from SavedResource s where s.listing.id = :listingId")
    List<UUID> findSaverIds(@Param("listingId") UUID listingId);
}

package com.bookbridge.listing;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingImageRepository extends JpaRepository<ListingImage, UUID> {
    boolean existsByMediaId(UUID mediaId);

    boolean existsByMediaIdAndListingIdNot(UUID mediaId, UUID listingId);

    @Modifying
    @Query("delete from ListingImage i where i.listing.id = :listingId")
    void deleteByListingId(@Param("listingId") UUID listingId);
}

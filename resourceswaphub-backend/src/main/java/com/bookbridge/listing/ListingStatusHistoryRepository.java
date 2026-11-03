package com.bookbridge.listing;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface ListingStatusHistoryRepository extends JpaRepository<ListingStatusHistory, UUID> {

    @EntityGraph(attributePaths = {"changedBy"})
    List<ListingStatusHistory> findByListingIdOrderByCreatedAtAsc(UUID listingId);
}

package com.bookbridge.collection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface CollectionItemRepository extends JpaRepository<CollectionItem, UUID> {

    @Query("select ci.listing.id from CollectionItem ci "
            + "where ci.collection.id = :collectionId order by ci.addedAt desc")
    List<UUID> findListingIdsByCollectionOrderByAddedAtDesc(@Param("collectionId") UUID collectionId);

    boolean existsByCollectionIdAndListingId(UUID collectionId, UUID listingId);


    Optional<CollectionItem> findByCollectionIdAndListingId(UUID collectionId, UUID listingId);

    @EntityGraph(attributePaths = {"listing"})
    @Query("select ci from CollectionItem ci where ci.collection.id = :collectionId")
    List<CollectionItem> findByCollectionId(@Param("collectionId") UUID collectionId);

    long countByCollectionId(UUID collectionId);
}

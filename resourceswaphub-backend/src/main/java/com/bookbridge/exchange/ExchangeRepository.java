package com.bookbridge.exchange;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeRepository extends JpaRepository<Exchange, UUID> {

    Optional<Exchange> findByListingIdAndStatusNot(UUID listingId, ExchangeStatus status);

    @EntityGraph(attributePaths = {"listing", "listing.book", "listing.owner", "participant", "conversation"})
    @Query("select e from Exchange e where e.owner.id = :userId or e.participant.id = :userId "
            + "order by e.updatedAt desc")
    Page<Exchange> forUser(@Param("userId") UUID userId, Pageable pageable);

    List<Exchange> findByListingIdOrderByCreatedAtDesc(UUID listingId);
}

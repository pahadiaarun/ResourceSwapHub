package com.bookbridge.collection;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectionRepository extends JpaRepository<Collection, UUID> {
    Page<Collection> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    @Query("select c from Collection c where c.id = :id")
    Optional<Collection> findWithOwnerById(@Param("id") UUID id);
}

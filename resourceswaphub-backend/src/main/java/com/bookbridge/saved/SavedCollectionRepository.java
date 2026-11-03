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

public interface SavedCollectionRepository extends JpaRepository<SavedCollection, SavedKey> {

    @EntityGraph(attributePaths = {"collection", "collection.owner"})
    @Query("select s from SavedCollection s where s.user.id = :userId order by s.createdAt desc")
    Page<SavedCollection> findForUser(@Param("userId") UUID userId, Pageable pageable);

    Optional<SavedCollection> findByUserIdAndCollectionId(UUID userId, UUID collectionId);
}

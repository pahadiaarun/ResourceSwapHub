package com.bookbridge.communication;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Optional<Conversation> findByDirectKey(String directKey);

    @EntityGraph(attributePaths = {"listing", "listing.book", "listing.owner", "listing.images", "listing.images.media"})
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findDetailedById(@Param("id") UUID id);

    @Query(value = """
            select c from Conversation c
            where exists (
                select 1 from ConversationParticipant p
                where p.conversation = c and p.user.id = :userId
            )
            order by c.lastMessageAt desc
            """,
            countQuery = """
            select count(c) from Conversation c
            where exists (
                select 1 from ConversationParticipant p
                where p.conversation = c and p.user.id = :userId
            )
            """)
    Page<Conversation> findForUser(@Param("userId") UUID userId, Pageable pageable);
}

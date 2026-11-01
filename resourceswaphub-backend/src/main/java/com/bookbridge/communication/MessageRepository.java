package com.bookbridge.communication;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {
    Page<Message> findByConversationIdOrderByIdDesc(java.util.UUID conversationId, Pageable pageable);

    Page<Message> findByConversationIdAndIdLessThanOrderByIdDesc(java.util.UUID conversationId, Long beforeId,
                                                                Pageable pageable);

    Optional<Message> findFirstByConversationIdOrderByIdDesc(java.util.UUID conversationId);

    @EntityGraph(attributePaths = "sender")
    @Query("""
            select m from Message m
            where m.id in (
                select max(m2.id) from Message m2
                where m2.conversation.id in :conversationIds
                group by m2.conversation.id
            )
            """)
    List<Message> findLatestForConversations(@Param("conversationIds") Collection<java.util.UUID> conversationIds);
}

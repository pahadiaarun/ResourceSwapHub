package com.bookbridge.communication;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, UUID> {
    Optional<ConversationParticipant> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);

    List<ConversationParticipant> findByConversationId(UUID conversationId);

    List<ConversationParticipant> findByConversationIdIn(Collection<UUID> conversationIds);

    interface UnreadRow {
        UUID getConversationId();

        long getUnread();
    }

    @Query(value = """
            SELECT m.conversation_id AS conversationId, COUNT(*) AS unread
            FROM messages m
            JOIN conversation_participants p
              ON p.conversation_id = m.conversation_id AND p.user_id = :userId
            WHERE m.conversation_id IN (:conversationIds)
              AND m.sender_id <> :userId
              AND (p.last_read_message_id IS NULL OR m.id > p.last_read_message_id)
            GROUP BY m.conversation_id
            """, nativeQuery = true)
    List<UnreadRow> unreadByConversationIds(@Param("userId") UUID userId,
                                            @Param("conversationIds") Collection<UUID> conversationIds);

    @Query(value = """
            SELECT COUNT(*)
            FROM messages m
            JOIN conversation_participants p
              ON p.conversation_id = m.conversation_id AND p.user_id = :userId
            WHERE m.sender_id <> :userId
              AND (p.last_read_message_id IS NULL OR m.id > p.last_read_message_id)
            """, nativeQuery = true)
    long unreadTotal(@Param("userId") UUID userId);

    @Query(value = """
            SELECT COUNT(*)
            FROM messages m
            JOIN conversation_participants p
              ON p.conversation_id = m.conversation_id AND p.user_id = :userId
            WHERE m.conversation_id = :conversationId
              AND m.sender_id <> :userId
              AND (p.last_read_message_id IS NULL OR m.id > p.last_read_message_id)
            """, nativeQuery = true)
    long unreadInConversation(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);
}

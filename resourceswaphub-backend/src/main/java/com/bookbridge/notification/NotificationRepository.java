package com.bookbridge.notification;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndTypeAndEntityId(UUID userId, NotificationType type, UUID entityId);

    boolean existsByUserIdAndTypeAndEntityIdAndReadAtIsNull(UUID userId, NotificationType type, UUID entityId);

    @Modifying
    @Query("update Notification n set n.readAt = CURRENT_TIMESTAMP where n.user.id = :userId and n.readAt is null")
    int markAllRead(@Param("userId") UUID userId);
}

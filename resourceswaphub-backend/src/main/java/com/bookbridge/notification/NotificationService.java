package com.bookbridge.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.shared.api.PageResponse;
import com.bookbridge.shared.error.ApiException;
import com.bookbridge.user.User;
import com.bookbridge.user.UserRepository;

/**
 * Database-backed notification centre. Listeners write rows inside the same
 * transaction as the event that caused them, so nothing is lost if the row is
 * written but the surrounding action fails.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates a notification. When {@code dedupeType} is set and the same
     * (user, type, entity) row already exists, nothing is written — retries and
     * republish events never spam the recipient twice.
     */
    @Transactional
    public boolean create(UUID userId, NotificationType type, String title, String body,
                          String linkPath, UUID entityId, boolean dedupeByEntity) {
        if (dedupeByEntity
                && notificationRepository.existsByUserIdAndTypeAndEntityId(userId, type, entityId)) {
            return false;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("That user could not be found."));
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(trimToNull(body, 500));
        notification.setLinkPath(trimToNull(linkPath, 300));
        notification.setEntityId(entityId);
        notificationRepository.save(notification);
        return true;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> list(UUID userId, Boolean unread, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 48)));
        Page<Notification> result = unread == null
                ? notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : unread
                        ? notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                        : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return new PageResponse<>(result.getContent().stream().map(NotificationDto::from).toList(),
                Math.max(0, page), Math.max(1, Math.min(size, 48)), result.getTotalElements(),
                result.getTotalPages(), result.hasNext());
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationDto markRead(UUID userId, UUID notificationId) {
        Notification notification = owned(userId, notificationId);
        if (notification.getReadAt() == null) {
            notification.setReadAt(java.time.Instant.now());
            notificationRepository.saveAndFlush(notification);
        }
        return NotificationDto.from(notification);
    }

    @Transactional
    public long markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId);
    }

    /** Whether any notification (read or unread) of this type/entity already exists for the user. */
    @Transactional(readOnly = true)
    public boolean exists(NotificationType type, UUID userId, UUID entityId) {
        return notificationRepository.existsByUserIdAndTypeAndEntityId(userId, type, entityId);
    }

    /**
     * Saved-listing lifecycle nudge. One unread notification per (saver, listing);
     * after it is read, a later genuine transition may nudge again but silent
     * retries within the same unread window never duplicate.
     */
    @Transactional
    public void notifySaversUnavailable(List<UUID> saverIds, UUID listingId) {
        for (UUID saverId : saverIds) {
            if (notificationRepository.existsByUserIdAndTypeAndEntityIdAndReadAtIsNull(
                    saverId, NotificationType.SAVED_LISTING_UNAVAILABLE, listingId)) {
                continue;
            }
            create(saverId, NotificationType.SAVED_LISTING_UNAVAILABLE,
                    "A saved resource is unavailable",
                    "A resource on your saved list is no longer available.",
                    "/shelf/saved", listingId, false);
        }
    }

    private Notification owned(UUID userId, UUID notificationId) {
        return notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("That notification could not be found."));
    }

    private String trimToNull(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private String trimToNull(String value) {
        return trimToNull(value, 500);
    }
}

package com.bookbridge.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
        UUID id,
        String type,
        String title,
        String body,
        String linkPath,
        UUID entityId,
        boolean read,
        Instant createdAt
) {
    public static NotificationDto from(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getBody(),
                notification.getLinkPath(),
                notification.getEntityId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}

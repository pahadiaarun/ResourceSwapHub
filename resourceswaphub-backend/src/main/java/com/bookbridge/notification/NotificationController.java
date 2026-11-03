package com.bookbridge.notification;

import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.shared.api.ApiResponse;
import com.bookbridge.shared.api.PageResponse;
import com.bookbridge.shared.security.SecurityUtils;

@RestController
@RequestMapping("/api/v1/users/me/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationDto>> list(
            @RequestParam(name = "unread", required = false) Boolean unread,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(notificationService.list(SecurityUtils.currentUserId(), unread, page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.ok(Map.of("count", notificationService.unreadCount(SecurityUtils.currentUserId())));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationDto> markRead(@PathVariable UUID id) {
        return ApiResponse.ok(notificationService.markRead(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/read-all")
    public ApiResponse<Map<String, Long>> markAllRead() {
        return ApiResponse.ok(Map.of("updated", notificationService.markAllRead(SecurityUtils.currentUserId())));
    }
}

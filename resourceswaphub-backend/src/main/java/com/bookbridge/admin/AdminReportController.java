package com.bookbridge.admin;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.moderation.ModerationActionType;
import com.bookbridge.moderation.ModerationService;
import com.bookbridge.moderation.ReportListDto;
import com.bookbridge.moderation.ReportReason;
import com.bookbridge.moderation.ReportStatus;
import com.bookbridge.moderation.ResourceReport;
import com.bookbridge.shared.api.ApiResponse;
import com.bookbridge.shared.api.PageResponse;
import com.bookbridge.shared.error.ApiException;
import com.bookbridge.shared.security.SecurityUtils;
import com.bookbridge.user.User;
import com.bookbridge.user.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Signed-in users can report listings; only users with the ADMIN role reach the
 * /admin review endpoints (the role is checked in ModerationService so the gate
 * stays enforced on the backend regardless of route configuration).
 */
@RestController
@RequestMapping("/api/v1")
public class AdminReportController {

    private final ModerationService moderationService;
    private final UserService userService;

    public AdminReportController(ModerationService moderationService, UserService userService) {
        this.moderationService = moderationService;
        this.userService = userService;
    }

    // ------------------------------------------------------------ user reports

    @PostMapping("/reports")
    public ResponseEntity<ApiResponse<ReportListDto>> report(@Valid @RequestBody ReportRequest request) {
        ResourceReport report = moderationService.createReport(
                SecurityUtils.currentUserId(), request.listingId(), request.reason(), request.details());
        User reporter = userService.require(report.getReporter().getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new ReportListDto(
                        report.getId(),
                        report.getListing().getId(),
                        report.getListing().getBook() != null
                                ? report.getListing().getBook().getTitle()
                                : report.getListing().getTitle(),
                        reporter.getId(),
                        reporter.getDisplayName(),
                        report.getReason().name(),
                        report.getDetails(),
                        report.getStatus().name(),
                        report.getCreatedAt())));
    }

    // --------------------------------------------------------- admin endpoints

    @GetMapping("/admin/reports")
    public ApiResponse<PageResponse<ReportListDto>> list(
            @RequestParam(name = "status", required = false) ReportStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "24") int size) {
        return ApiResponse.ok(moderationService.listReports(status, page, size, SecurityUtils.currentUserId()));
    }

    @PatchMapping("/admin/reports/{id}")
    public ApiResponse<ReportListDto> resolve(@PathVariable UUID id,
                                               @Valid @RequestBody ResolveRequest request) {
        ResourceReport report = moderationService.resolve(SecurityUtils.currentUserId(), id, request.status());
        return ApiResponse.ok(new ReportListDto(
                report.getId(),
                report.getListing().getId(),
                report.getListing().getBook() != null
                        ? report.getListing().getBook().getTitle()
                        : report.getListing().getTitle(),
                report.getReporter().getId(),
                report.getReporter().getDisplayName(),
                report.getReason().name(),
                report.getDetails(),
                report.getStatus().name(),
                report.getCreatedAt()
        ));
    }

    @PostMapping("/admin/listings/{id}/moderation")
    public ApiResponse<ModerationService.ListingModerationDto> moderate(
            @PathVariable UUID id,
            @Valid @RequestBody ModerationRequest request) {
        ModerationActionType action = request.action();
        if (action == null) {
            throw ApiException.badRequest("ACTION_REQUIRED", "Choose a moderation action.");
        }
        return ApiResponse.ok(moderationService.moderate(
                SecurityUtils.currentUserId(), id, action, request.reason()));
    }

    public record ReportRequest(
            @NotNull(message = "listingId is required")
            UUID listingId,
            @NotNull(message = "reason is required")
            ReportReason reason,
            String details
    ) {
    }

    public record ResolveRequest(
            @NotNull(message = "status is required")
            ReportStatus status
    ) {
    }

    public record ModerationRequest(
            @NotNull(message = "action is required")
            ModerationActionType action,
            String reason
    ) {
    }
}

package com.bookbridge.moderation;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.listing.Listing;
import com.bookbridge.listing.ListingRepository;
import com.bookbridge.listing.ModerationState;
import com.bookbridge.shared.api.PageResponse;
import com.bookbridge.shared.error.ApiException;
import com.bookbridge.user.User;
import com.bookbridge.user.UserService;

/**
 * Community reporting plus admin-only review. Every moderation decision is
 * appended to the moderation_actions audit trail.
 */
@Service
public class ModerationService {

    private final ResourceReportRepository reportRepository;
    private final ModerationActionRepository actionRepository;
    private final ListingRepository listingRepository;
    private final UserService userService;

    public ModerationService(ResourceReportRepository reportRepository,
                             ModerationActionRepository actionRepository,
                             ListingRepository listingRepository, UserService userService) {
        this.reportRepository = reportRepository;
        this.actionRepository = actionRepository;
        this.listingRepository = listingRepository;
        this.userService = userService;
    }

    // ------------------------------------------------------------------ reports

    @Transactional
    public ResourceReport createReport(UUID reporterId, UUID listingId, ReportReason reason, String details) {
        User reporter = userService.require(reporterId);
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> ApiException.notFound("That listing could not be found."));
        if (listing.getOwner().getId().equals(reporterId)) {
            throw ApiException.badRequest("OWN_LISTING", "You cannot report your own listing.");
        }
        if (reason == null) {
            throw ApiException.badRequest("REASON_REQUIRED", "Choose a reason for the report.");
        }
        if (reportRepository.existsByListingIdAndReporterIdAndStatus(listingId, reporterId, ReportStatus.OPEN)) {
            throw ApiException.conflict("REPORT_EXISTS", "You already reported this listing; it is under review.");
        }
        ResourceReport report = new ResourceReport();
        report.setListing(listing);
        report.setReporter(reporter);
        report.setReason(reason);
        report.setDetails(trim(details, 1000));
        return reportRepository.saveAndFlush(report);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportListDto> listReports(ReportStatus status, int page, int size, UUID adminId) {
        requireAdmin(adminId);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 48)));
        Page<ResourceReport> result = reportRepository.pageByStatus(status, pageable);
        Page<ReportListDto> mapped = result.map(ReportListDto::from);
        return new PageResponse<>(mapped.getContent(), Math.max(0, page), Math.max(1, Math.min(size, 48)),
                result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }

    /** Marks the report reviewed / actioned / dismissed (admin only). */
    @Transactional
    public ResourceReport resolve(UUID adminId, UUID reportId, ReportStatus status) {
        requireAdmin(adminId);
        ResourceReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> ApiException.notFound("That report could not be found."));
        if (status == ReportStatus.OPEN) {
            throw ApiException.badRequest("INVALID_STATUS", "Reports cannot be reopened.");
        }
        report.setStatus(status);
        report.setReviewedBy(userService.require(adminId));
        report.setResolvedAt(Instant.now());
        return reportRepository.saveAndFlush(report);
    }

    // --------------------------------------------------------------- moderation

    /** Admin-only: restrict, remove or restore a listing. Audited in moderation_actions. */
    @Transactional
    public ListingModerationDto moderate(UUID adminId, UUID listingId, ModerationActionType action, String reason) {
        User admin = requireAdmin(adminId);
        Listing listing = listingRepository.findWithDetailsById(listingId)
                .orElseThrow(() -> ApiException.notFound("That listing could not be found."));
        ModerationState target = switch (action) {
            case RESTRICT -> ModerationState.RESTRICTED;
            case REMOVE -> ModerationState.REMOVED;
            case RESTORE -> ModerationState.NONE;
        };
        if (action == ModerationActionType.REMOVE) {
            // Hide immediately but keep the status audit trail in the listing's own history.
            listing.setModerationState(ModerationState.REMOVED);
        } else {
            listing.setModerationState(target);
        }
        if (action != ModerationActionType.RESTORE) {
            listing.setStatus(listing.getStatus());
        }
        listingRepository.saveAndFlush(listing);

        ModerationAction audit = new ModerationAction();
        audit.setListing(listing);
        audit.setActor(admin);
        audit.setAction(action);
        audit.setReason(trim(reason, 500));
        actionRepository.save(audit);
        return new ListingModerationDto(listing.getId(), listing.getModerationState().name());
    }

    private User requireAdmin(UUID userId) {
        User user = userService.require(userId);
        if (!"ADMIN".equals(user.getRole())) {
            throw ApiException.forbidden("Admin access is required for moderation.");
        }
        return user;
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    public record ListingModerationDto(UUID listingId, String moderationState) {
    }
}

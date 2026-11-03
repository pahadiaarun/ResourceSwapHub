package com.bookbridge.moderation;

import java.time.Instant;
import java.util.UUID;

public record ReportListDto(
        UUID id,
        UUID listingId,
        String listingTitle,
        UUID reporterId,
        String reporterName,
        String reason,
        String details,
        String status,
        Instant createdAt
) {
    public static ReportListDto from(ResourceReport report) {
        return new ReportListDto(
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
        );
    }
}

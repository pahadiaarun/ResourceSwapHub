package com.bookbridge.shared.api;

import java.util.List;

public record ApiErrorResponse(ApiError error) {

    public record ApiError(
            String code,
            String message,
            List<FieldIssue> details,
            String traceId
    ) {
    }

    public record FieldIssue(String field, String message) {
    }

    public static ApiErrorResponse of(String code, String message, String traceId) {
        return new ApiErrorResponse(new ApiError(code, message, List.of(), traceId));
    }

    public static ApiErrorResponse of(String code, String message, List<FieldIssue> details, String traceId) {
        return new ApiErrorResponse(new ApiError(code, message, details == null ? List.of() : details, traceId));
    }
}

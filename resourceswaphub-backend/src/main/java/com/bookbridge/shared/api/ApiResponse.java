package com.bookbridge.shared.api;

import java.util.List;
import java.util.Map;

public record ApiResponse<T>(T data, Map<String, Object> meta) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, Map.of());
    }

    public static <T> ApiResponse<T> withMeta(T data, Map<String, Object> meta) {
        return new ApiResponse<>(data, meta == null ? Map.of() : meta);
    }

    public static <T> ApiResponse<PageResponse<T>> page(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        PageResponse<T> response = new PageResponse<>(
                content,
                page,
                size,
                totalElements,
                totalPages,
                page + 1 < totalPages
        );
        return new ApiResponse<>(response, Map.of());
    }
}

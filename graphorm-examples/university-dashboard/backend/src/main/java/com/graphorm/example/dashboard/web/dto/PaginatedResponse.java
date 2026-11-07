package com.graphorm.example.dashboard.web.dto;

import java.util.List;

/**
 * Universal DTO for paginated database query responses.
 *
 * @param <T>         the item entity type
 * @param items       the paginated list of items
 * @param totalItems  the total count matching filter criteria in the database
 * @param currentPage the current 1-indexed page
 * @param pageSize    the page size (limit)
 * @param totalPages  the computed total page count
 */
public record PaginatedResponse<T>(
        List<T> items,
        long totalItems,
        int currentPage,
        int pageSize,
        int totalPages
) {
    public PaginatedResponse(List<T> items, long totalItems, int currentPage, int pageSize) {
        this(
                items,
                totalItems,
                currentPage,
                pageSize,
                pageSize > 0 ? (int) Math.max(1, Math.ceil((double) totalItems / pageSize)) : 1
        );
    }
}

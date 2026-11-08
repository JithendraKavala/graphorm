package com.graphorm.graphql.pagination;

/**
 * Relay-compliant PageInfo model representing pagination state.
 */
public record PageInfo(
        boolean hasNextPage,
        boolean hasPreviousPage,
        String startCursor,
        String endCursor
) {}

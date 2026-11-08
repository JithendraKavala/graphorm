package com.graphorm.graphql.pagination;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Relay-compliant Connection model containing edges, pageInfo, and optional totalCount.
 */
public record Connection<T>(
        List<Edge<T>> edges,
        PageInfo pageInfo,
        Integer totalCount
) {
    public Connection {
        edges = edges == null ? List.of() : Collections.unmodifiableList(edges);
        Objects.requireNonNull(pageInfo, "PageInfo must not be null");
    }

    public static <T> Connection<T> empty() {
        return new Connection<>(List.of(), new PageInfo(false, false, null, null), 0);
    }
}

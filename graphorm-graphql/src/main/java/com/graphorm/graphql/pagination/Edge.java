package com.graphorm.graphql.pagination;

import java.util.Objects;

/**
 * Relay-compliant Edge model wrapping a node with its opaque cursor.
 */
public record Edge<T>(
        T node,
        String cursor
) {
    public Edge {
        Objects.requireNonNull(node, "Node must not be null");
        Objects.requireNonNull(cursor, "Cursor must not be null");
    }
}

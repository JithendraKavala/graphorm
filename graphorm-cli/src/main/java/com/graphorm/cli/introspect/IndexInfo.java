package com.graphorm.cli.introspect;

import java.util.List;
import java.util.Objects;

/**
 * Metadata about an introspected database index.
 */
public record IndexInfo(
        String name,
        List<String> columns,
        boolean isUnique,
        String type
) {
    public IndexInfo {
        Objects.requireNonNull(name, "name must not be null");
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}

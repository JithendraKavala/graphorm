package com.graphorm.cli.introspect;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Metadata about an introspected PostgreSQL enum type.
 */
public record EnumInfo(
        String name,
        List<String> values
) {
    public EnumInfo {
        Objects.requireNonNull(name, "Enum name must not be null");
        values = values == null ? List.of() : List.copyOf(values);
    }
}

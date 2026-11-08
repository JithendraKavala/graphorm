package com.graphorm.cli.introspect;

import java.util.Objects;

/**
 * Metadata about an introspected check constraint.
 */
public record CheckConstraintInfo(
        String name,
        String expression
) {
    public CheckConstraintInfo {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(expression, "expression must not be null");
    }
}

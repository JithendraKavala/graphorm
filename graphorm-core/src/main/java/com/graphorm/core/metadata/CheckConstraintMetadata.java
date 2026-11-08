package com.graphorm.core.metadata;

import java.util.Objects;

/**
 * Immutable metadata record representing a table check constraint.
 *
 * @param name       the constraint name (e.g. "chk_student_age")
 * @param expression the boolean SQL check expression (e.g. "age >= 18")
 */
public record CheckConstraintMetadata(
        String name,
        String expression
) {

    public CheckConstraintMetadata {
        Objects.requireNonNull(name, "Check constraint name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Check constraint name must not be blank");
        }
        Objects.requireNonNull(expression, "Check constraint expression must not be null");
        if (expression.isBlank()) {
            throw new IllegalArgumentException("Check constraint expression must not be blank");
        }
    }
}

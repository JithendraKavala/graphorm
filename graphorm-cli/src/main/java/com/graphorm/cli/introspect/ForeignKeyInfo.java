package com.graphorm.cli.introspect;

import java.util.Objects;

/**
 * Metadata about an introspected foreign key relationship.
 */
public record ForeignKeyInfo(
        String constraintName,
        String sourceTable,
        String sourceColumn,
        String targetTable,
        String targetColumn
) {
    public ForeignKeyInfo {
        Objects.requireNonNull(constraintName, "constraintName must not be null");
        Objects.requireNonNull(sourceTable, "sourceTable must not be null");
        Objects.requireNonNull(sourceColumn, "sourceColumn must not be null");
        Objects.requireNonNull(targetTable, "targetTable must not be null");
        Objects.requireNonNull(targetColumn, "targetColumn must not be null");
    }
}

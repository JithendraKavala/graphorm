package com.graphorm.cli.introspect;

import java.util.Objects;

/**
 * Metadata about an introspected column, including native type facets (length, precision, scale).
 */
public record ColumnInfo(
        String tableName,
        String columnName,
        String dataType,
        String udtName,
        boolean isNullable,
        String columnDefault,
        int ordinalPosition,
        Integer characterMaximumLength,
        Integer numericPrecision,
        Integer numericScale
) {
    public ColumnInfo {
        Objects.requireNonNull(tableName, "tableName must not be null");
        Objects.requireNonNull(columnName, "columnName must not be null");
        Objects.requireNonNull(dataType, "dataType must not be null");
    }

    public ColumnInfo(
            String tableName,
            String columnName,
            String dataType,
            String udtName,
            boolean isNullable,
            String columnDefault,
            int ordinalPosition
    ) {
        this(tableName, columnName, dataType, udtName, isNullable, columnDefault, ordinalPosition, null, null, null);
    }
}

package com.graphorm.cli.introspect;

import java.util.Objects;

/**
 * Metadata about an introspected column.
 */
public record ColumnInfo(
        String tableName,
        String columnName,
        String dataType,
        String udtName,
        boolean isNullable,
        String columnDefault,
        int ordinalPosition
) {
    public ColumnInfo {
        Objects.requireNonNull(tableName, "tableName must not be null");
        Objects.requireNonNull(columnName, "columnName must not be null");
        Objects.requireNonNull(dataType, "dataType must not be null");
    }
}

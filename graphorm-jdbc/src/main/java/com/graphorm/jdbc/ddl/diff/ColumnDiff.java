package com.graphorm.jdbc.ddl.diff;

import com.graphorm.jdbc.ddl.ColumnDefinition;

import java.util.Objects;

/**
 * Captures changes between an existing baseline column and a desired target column.
 *
 * @param columnName           physical column name
 * @param oldColumn            existing column definition in baseline
 * @param newColumn            target column definition
 * @param typeChanged          whether data type has changed
 * @param nullabilityChanged   whether nullability has changed
 * @param defaultValueChanged  whether default value expression has changed
 */
public record ColumnDiff(
        String columnName,
        ColumnDefinition oldColumn,
        ColumnDefinition newColumn,
        boolean typeChanged,
        boolean nullabilityChanged,
        boolean defaultValueChanged
) {

    public ColumnDiff {
        Objects.requireNonNull(columnName, "Column name must not be null");
        Objects.requireNonNull(oldColumn, "Old column must not be null");
        Objects.requireNonNull(newColumn, "New column must not be null");
    }

    public boolean hasChanges() {
        return typeChanged || nullabilityChanged || defaultValueChanged;
    }
}

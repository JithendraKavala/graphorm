package com.graphorm.jdbc.ddl.diff;

import java.util.Objects;

/**
 * Represents a potentially destructive schema alteration that could cause data loss or query failure.
 */
public record DestructiveChange(
        DestructiveType type,
        String tableName,
        String objectName,
        String description,
        boolean isError
) {

    public DestructiveChange {
        Objects.requireNonNull(type, "DestructiveType must not be null");
        Objects.requireNonNull(objectName, "ObjectName must not be null");
        Objects.requireNonNull(description, "Description must not be null");
    }

    public enum DestructiveType {
        DROP_TABLE,
        DROP_COLUMN,
        DROP_ENUM,
        COLUMN_TYPE_CHANGE,
        NULLABLE_TO_NOT_NULL,
        DROP_UNIQUE_CONSTRAINT,
        DROP_CHECK_CONSTRAINT,
        DROP_FOREIGN_KEY
    }

    public static DestructiveChange dropTable(String tableName) {
        return new DestructiveChange(
                DestructiveType.DROP_TABLE,
                tableName,
                tableName,
                "Table '" + tableName + "' will be dropped, resulting in total data loss.",
                true
        );
    }

    public static DestructiveChange dropColumn(String tableName, String columnName) {
        return new DestructiveChange(
                DestructiveType.DROP_COLUMN,
                tableName,
                columnName,
                "Column '" + columnName + "' on table '" + tableName + "' will be dropped, resulting in data loss.",
                true
        );
    }

    public static DestructiveChange dropEnum(String enumName) {
        return new DestructiveChange(
                DestructiveType.DROP_ENUM,
                null,
                enumName,
                "Enum type '" + enumName + "' will be dropped.",
                true
        );
    }

    public static DestructiveChange typeChange(String tableName, String columnName, String oldType, String newType) {
        return new DestructiveChange(
                DestructiveType.COLUMN_TYPE_CHANGE,
                tableName,
                columnName,
                "Column '" + columnName + "' on table '" + tableName + "' changes type from " + oldType + " to " + newType + " which may cause data truncation or cast failure.",
                false
        );
    }

    public static DestructiveChange nullableToNotNull(String tableName, String columnName) {
        return new DestructiveChange(
                DestructiveType.NULLABLE_TO_NOT_NULL,
                tableName,
                columnName,
                "Column '" + columnName + "' on table '" + tableName + "' becomes NOT NULL. If existing rows contain NULL values, migration will fail.",
                false
        );
    }
}

package com.graphorm.core.metadata;

import java.util.Locale;

/**
 * Supported index access method/type.
 */
public enum IndexType {
    BTREE("BTREE"),
    HASH("HASH"),
    GIN("GIN"),
    GIST("GIST");

    private final String sql;

    IndexType(String sql) {
        this.sql = sql;
    }

    public String toSql() {
        return sql;
    }

    public static IndexType from(String value) {
        if (value == null || value.isBlank()) {
            return BTREE;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "BTREE", "B_TREE" -> BTREE;
            case "HASH" -> HASH;
            case "GIN" -> GIN;
            case "GIST" -> GIST;
            default -> throw new IllegalArgumentException("Unknown index type: " + value);
        };
    }
}

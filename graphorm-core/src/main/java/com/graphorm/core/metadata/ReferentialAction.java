package com.graphorm.core.metadata;

import java.util.Locale;

/**
 * SQL referential action defining constraint behavior on foreign key updates and deletes.
 */
public enum ReferentialAction {
    CASCADE("CASCADE"),
    RESTRICT("RESTRICT"),
    SET_NULL("SET NULL"),
    SET_DEFAULT("SET DEFAULT"),
    NO_ACTION("NO ACTION");

    private final String sql;

    ReferentialAction(String sql) {
        this.sql = sql;
    }

    /**
     * The SQL representation of this referential action (e.g. "CASCADE", "SET NULL").
     */
    public String toSql() {
        return sql;
    }

    /**
     * Parses a string representation into a {@link ReferentialAction}.
     * Accepts forms like "Cascade", "cascade", "SetNull", "set_null", "SET NULL", "RESTRICT", etc.
     *
     * @param value the string value to parse
     * @return the corresponding ReferentialAction, or NO_ACTION if null/empty
     */
    public static ReferentialAction from(String value) {
        if (value == null || value.isBlank()) {
            return NO_ACTION;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        return switch (normalized) {
            case "CASCADE" -> CASCADE;
            case "RESTRICT" -> RESTRICT;
            case "SETNULL", "SET_NULL" -> SET_NULL;
            case "SETDEFAULT", "SET_DEFAULT" -> SET_DEFAULT;
            case "NOACTION", "NO_ACTION" -> NO_ACTION;
            default -> throw new IllegalArgumentException("Unknown referential action: " + value);
        };
    }
}

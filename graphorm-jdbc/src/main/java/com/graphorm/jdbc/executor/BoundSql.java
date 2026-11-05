package com.graphorm.jdbc.executor;

import java.util.List;
import java.util.Objects;

/**
 * Immutable representation of a fully parameterized SQL statement and its ordered parameter values.
 * Guarantees zero raw value interpolation in SQL strings.
 *
 * @param sql        the parameterized SQL string with positional '?' placeholders
 * @param parameters the ordered list of bound parameter values
 */
public record BoundSql(String sql, List<Object> parameters) {

    public BoundSql {
        Objects.requireNonNull(sql, "SQL statement must not be null");
        if (sql.isBlank()) {
            throw new IllegalArgumentException("SQL statement must not be blank");
        }
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    /**
     * Creates a {@link BoundSql} without parameters.
     *
     * @param sql the SQL string
     * @return a new BoundSql instance
     */
    public static BoundSql of(String sql) {
        return new BoundSql(sql, List.of());
    }

    /**
     * Creates a {@link BoundSql} with the given parameters.
     *
     * @param sql        the SQL string
     * @param parameters the bound parameters
     * @return a new BoundSql instance
     */
    public static BoundSql of(String sql, List<Object> parameters) {
        return new BoundSql(sql, parameters);
    }

    /**
     * Creates a {@link BoundSql} with varargs parameters.
     *
     * @param sql        the SQL string
     * @param parameters the bound parameters
     * @return a new BoundSql instance
     */
    public static BoundSql of(String sql, Object... parameters) {
        return new BoundSql(sql, parameters != null ? List.of(parameters) : List.of());
    }
}

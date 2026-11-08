package com.graphorm.core.query;

import java.util.List;
import java.util.Objects;

/**
 * Strongly typed AST representation of a Common Table Expression (CTE):
 * {@code WITH [RECURSIVE] <name> [(col1, col2)] AS (<query>)}.
 *
 * @param name          the CTE name / alias
 * @param columnAliases optional column aliases for the CTE
 * @param query         the inner query (SelectQuery, SqlFragment, etc.)
 * @param recursive     whether this is a recursive CTE definition
 */
public record CommonTableExpression(
        String name,
        List<String> columnAliases,
        SqlQuery query,
        boolean recursive
) {

    public CommonTableExpression {
        Objects.requireNonNull(name, "CTE name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("CTE name must not be blank");
        }
        Objects.requireNonNull(query, "CTE query must not be null");
        columnAliases = columnAliases == null ? List.of() : List.copyOf(columnAliases);
    }

    public static CommonTableExpression of(String name, SqlQuery query) {
        return new CommonTableExpression(name, List.of(), query, false);
    }

    public static CommonTableExpression of(String name, List<String> columnAliases, SqlQuery query) {
        return new CommonTableExpression(name, columnAliases, query, false);
    }

    public static CommonTableExpression recursive(String name, SqlQuery query) {
        return new CommonTableExpression(name, List.of(), query, true);
    }

    public static CommonTableExpression recursive(String name, List<String> columnAliases, SqlQuery query) {
        return new CommonTableExpression(name, columnAliases, query, true);
    }
}

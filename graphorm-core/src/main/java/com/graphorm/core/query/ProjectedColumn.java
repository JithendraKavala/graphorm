package com.graphorm.core.query;

import java.util.Objects;

/**
 * Strongly typed representation of a projected column or expression in a SELECT query.
 *
 * @param expression         the SQL expression or column name (e.g. "s.id", "COUNT(e.id)")
 * @param alias              the output alias (e.g. "studentId"), or null if not aliased
 * @param isHiddenDependency true if this column was injected by the query planner as a hidden
 *                           dependency (e.g., for GraphQL DataLoader relationship resolution)
 *                           and must be stripped before producing the final client response
 */
public record ProjectedColumn(
        String expression,
        String alias,
        boolean isHiddenDependency
) {

    public ProjectedColumn {
        Objects.requireNonNull(expression, "Column expression must not be null");
        if (expression.isBlank()) {
            throw new IllegalArgumentException("Column expression must not be blank");
        }
    }

    /**
     * Creates an unaliased, visible projected column.
     *
     * @param expression the column expression
     * @return a new ProjectedColumn
     */
    public static ProjectedColumn of(String expression) {
        return new ProjectedColumn(expression, null, false);
    }

    /**
     * Creates an aliased, visible projected column.
     *
     * @param expression the column expression
     * @param alias      the output alias
     * @return a new ProjectedColumn
     */
    public static ProjectedColumn of(String expression, String alias) {
        return new ProjectedColumn(expression, alias, false);
    }

    /**
     * Creates a hidden dependency column injected for downstream batch resolution.
     *
     * @param expression the column expression
     * @param alias      the output alias
     * @return a new ProjectedColumn marked as hidden
     */
    public static ProjectedColumn hidden(String expression, String alias) {
        return new ProjectedColumn(expression, alias, true);
    }
}

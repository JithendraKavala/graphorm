package com.graphorm.core.query;

import java.util.Objects;

/**
 * Strongly typed representation of an individual column or expression in a GROUP BY clause.
 *
 * @param expression the column name, alias, or expression to group by
 */
public record GroupByClause(String expression) {

    public GroupByClause {
        Objects.requireNonNull(expression, "Group by expression must not be null");
        if (expression.isBlank()) {
            throw new IllegalArgumentException("Group by expression must not be blank");
        }
    }

    /**
     * Creates a new {@link GroupByClause} for the given expression.
     *
     * @param expression the expression
     * @return a new GroupByClause
     */
    public static GroupByClause of(String expression) {
        return new GroupByClause(expression);
    }
}

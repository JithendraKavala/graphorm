package com.graphorm.jdbc.builder;

import com.graphorm.core.query.NullsOrder;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.jdbc.dialect.SqlDialect;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Builds SQL snippets for ORDER BY sorting criteria with direction and nulls positioning.
 */
public final class OrderByClauseBuilder {

    private OrderByClauseBuilder() {}

    /**
     * Builds an ORDER BY clause string for the given list of clauses.
     *
     * @param clauses the order-by criteria
     * @param dialect the SQL dialect
     * @return the formatted ORDER BY clause (e.g. " ORDER BY \"id\" ASC"), or empty string if empty
     */
    public static String buildOrderByClause(List<OrderByClause> clauses, SqlDialect dialect) {
        if (clauses == null || clauses.isEmpty()) {
            return "";
        }
        Objects.requireNonNull(dialect, "Dialect must not be null");

        StringBuilder sql = new StringBuilder(" ORDER BY ");
        for (int i = 0; i < clauses.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            OrderByClause clause = clauses.get(i);
            Objects.requireNonNull(clause, "OrderByClause element must not be null");
            String quotedExpr = dialect.quoteIdentifier(clause.expression());
            sql.append(quotedExpr).append(" ").append(clause.direction().name());
            if (clause.nullsOrder() == NullsOrder.FIRST) {
                sql.append(" NULLS FIRST");
            } else if (clause.nullsOrder() == NullsOrder.LAST) {
                sql.append(" NULLS LAST");
            }
        }
        return sql.toString();
    }

    /**
     * Builds an ORDER BY clause string for the given array of clauses.
     *
     * @param dialect the SQL dialect
     * @param clauses the order-by criteria
     * @return the formatted ORDER BY clause, or empty string if empty
     */
    public static String buildOrderByClause(SqlDialect dialect, OrderByClause... clauses) {
        if (clauses == null || clauses.length == 0) {
            return "";
        }
        return buildOrderByClause(Arrays.asList(clauses), dialect);
    }

    /**
     * Alias for {@link #buildOrderByClause(List, SqlDialect)}.
     *
     * @param clauses the order-by criteria
     * @param dialect the SQL dialect
     * @return the formatted ORDER BY clause
     */
    public static String build(List<OrderByClause> clauses, SqlDialect dialect) {
        return buildOrderByClause(clauses, dialect);
    }

    /**
     * Alias for {@link #buildOrderByClause(SqlDialect, OrderByClause...)}.
     *
     * @param dialect the SQL dialect
     * @param clauses the order-by criteria
     * @return the formatted ORDER BY clause
     */
    public static String build(SqlDialect dialect, OrderByClause... clauses) {
        return buildOrderByClause(dialect, clauses);
    }
}

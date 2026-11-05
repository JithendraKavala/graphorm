package com.graphorm.jdbc.builder;

import com.graphorm.core.query.NullsOrder;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.jdbc.dialect.SqlDialect;

import java.util.List;
import java.util.Objects;

/**
 * Builds SQL snippets for ORDER BY sorting criteria.
 */
public final class OrderByClauseBuilder {

    private OrderByClauseBuilder() {}

    /**
     * Builds an ORDER BY clause string for the given list of clauses.
     *
     * @param clauses the order-by criteria
     * @param dialect the SQL dialect
     * @return the formatted ORDER BY clause, or empty string if empty
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
}

package com.graphorm.core.query;

import java.util.Objects;

/**
 * Strongly typed representation of a JOIN clause within a SELECT query.
 *
 * @param joinType  the SQL join type (INNER, LEFT, RIGHT, FULL)
 * @param table     the source table or view being joined
 * @param alias     the table alias in the query
 * @param condition the boolean join predicate
 */
public record JoinClause(
        JoinType joinType,
        SourceTable table,
        String alias,
        WherePredicate condition
) {

    public JoinClause {
        Objects.requireNonNull(joinType, "Join type must not be null");
        Objects.requireNonNull(table, "Join table must not be null");
        Objects.requireNonNull(alias, "Join alias must not be null");
        if (alias.isBlank()) {
            throw new IllegalArgumentException("Join alias must not be blank");
        }
        Objects.requireNonNull(condition, "Join condition must not be null");
    }

    public static JoinClause inner(SourceTable table, String alias, WherePredicate condition) {
        return new JoinClause(JoinType.INNER, table, alias, condition);
    }

    public static JoinClause left(SourceTable table, String alias, WherePredicate condition) {
        return new JoinClause(JoinType.LEFT, table, alias, condition);
    }

    public static JoinClause right(SourceTable table, String alias, WherePredicate condition) {
        return new JoinClause(JoinType.RIGHT, table, alias, condition);
    }

    public static JoinClause full(SourceTable table, String alias, WherePredicate condition) {
        return new JoinClause(JoinType.FULL, table, alias, condition);
    }

    public static JoinClause inner(String tableName, String alias, WherePredicate condition) {
        return inner(SourceTable.table(tableName), alias, condition);
    }

    public static JoinClause left(String tableName, String alias, WherePredicate condition) {
        return left(SourceTable.table(tableName), alias, condition);
    }

    public static JoinClause right(String tableName, String alias, WherePredicate condition) {
        return right(SourceTable.table(tableName), alias, condition);
    }
}

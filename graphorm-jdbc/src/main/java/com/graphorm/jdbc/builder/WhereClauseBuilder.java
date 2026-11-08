package com.graphorm.jdbc.builder;

import com.graphorm.core.query.ComparisonOperator;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds parameterized SQL for {@link WherePredicate} trees with 100% parameter extraction.
 */
public final class WhereClauseBuilder {

    private WhereClauseBuilder() {}

    /**
     * Builds a parameterized WHERE clause starting with " WHERE " if a predicate is present.
     *
     * @param predicate the predicate tree (may be null)
     * @param dialect   the SQL dialect to use
     * @return a {@link BoundSql} containing the WHERE clause snippet and bound parameters
     */
    public static BoundSql buildWhereClause(WherePredicate predicate, SqlDialect dialect) {
        if (predicate == null) {
            return BoundSql.empty();
        }
        BoundSql inner = build(predicate, dialect);
        if (inner.sql().isBlank()) {
            return BoundSql.empty();
        }
        return new BoundSql(" WHERE " + inner.sql(), inner.parameters());
    }

    /**
     * Builds the parameterized condition string and parameter list without the leading "WHERE".
     *
     * @param predicate the predicate tree (must not be null)
     * @param dialect   the SQL dialect
     * @return the parameterized {@link BoundSql}
     */
    public static BoundSql build(WherePredicate predicate, SqlDialect dialect) {
        Objects.requireNonNull(predicate, "Predicate must not be null");
        Objects.requireNonNull(dialect, "Dialect must not be null");

        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>();
        renderPredicate(predicate, dialect, sql, parameters);
        return new BoundSql(sql.toString(), parameters);
    }

    private static void renderPredicate(
            WherePredicate predicate,
            SqlDialect dialect,
            StringBuilder sql,
            List<Object> parameters
    ) {
        if (predicate instanceof WherePredicate.BinaryPredicate binary) {
            renderBinary(binary, dialect, sql, parameters);
        } else if (predicate instanceof WherePredicate.ColumnComparisonPredicate colComp) {
            renderColumnComparison(colComp, dialect, sql);
        } else if (predicate instanceof WherePredicate.RowComparisonPredicate rowComp) {
            renderRowComparison(rowComp, dialect, sql, parameters);
        } else if (predicate instanceof WherePredicate.AndPredicate and) {
            renderAnd(and, dialect, sql, parameters);
        } else if (predicate instanceof WherePredicate.OrPredicate or) {
            renderOr(or, dialect, sql, parameters);
        } else if (predicate instanceof WherePredicate.NotPredicate not) {
            renderNot(not, dialect, sql, parameters);
        } else if (predicate instanceof WherePredicate.InPredicate in) {
            renderIn(in, dialect, sql, parameters);
        } else if (predicate instanceof WherePredicate.IsNullPredicate isNull) {
            renderIsNull(isNull, dialect, sql);
        } else if (predicate instanceof WherePredicate.BetweenPredicate between) {
            renderBetween(between, dialect, sql, parameters);
        } else if (predicate instanceof WherePredicate.RawPredicate raw) {
            // SECURITY WARNING (SEC-05): RawPredicate bypasses AST identifier quoting and parameter sanitization.
            // It must ONLY be constructed with static, developer-controlled SQL templates with positional '?' placeholders.
            // Never interpolate or concatenate untrusted user input directly into RawPredicate.sql().
            sql.append(raw.sql());
            parameters.addAll(raw.parameters());
        } else {
            throw new UnsupportedOperationException("Unknown predicate type: " + predicate.getClass().getName());
        }
    }

    private static void renderColumnComparison(
            WherePredicate.ColumnComparisonPredicate comp,
            SqlDialect dialect,
            StringBuilder sql
    ) {
        String quotedLeft = dialect.quoteIdentifier(comp.leftColumn());
        String quotedRight = dialect.quoteIdentifier(comp.rightColumn());
        String opSymbol = switch (comp.operator()) {
            case EQUALS -> "=";
            case NOT_EQUALS -> "!=";
            case GREATER_THAN -> ">";
            case GREATER_THAN_OR_EQUAL -> ">=";
            case LESS_THAN -> "<";
            case LESS_THAN_OR_EQUAL -> "<=";
            case LIKE -> "LIKE";
            case ILIKE -> "ILIKE";
        };
        sql.append(quotedLeft).append(" ").append(opSymbol).append(" ").append(quotedRight);
    }

    private static void renderRowComparison(
            WherePredicate.RowComparisonPredicate rowComp,
            SqlDialect dialect,
            StringBuilder sql,
            List<Object> parameters
    ) {
        sql.append("(");
        for (int i = 0; i < rowComp.columns().size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(dialect.quoteIdentifier(rowComp.columns().get(i)));
        }
        sql.append(") ");

        String opSymbol = switch (rowComp.operator()) {
            case EQUALS -> "=";
            case NOT_EQUALS -> "!=";
            case GREATER_THAN -> ">";
            case GREATER_THAN_OR_EQUAL -> ">=";
            case LESS_THAN -> "<";
            case LESS_THAN_OR_EQUAL -> "<=";
            case LIKE -> "LIKE";
            case ILIKE -> "ILIKE";
        };
        sql.append(opSymbol).append(" (");
        for (int i = 0; i < rowComp.values().size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
            parameters.add(rowComp.values().get(i));
        }
        sql.append(")");
    }

    private static void renderBinary(
            WherePredicate.BinaryPredicate binary,
            SqlDialect dialect,
            StringBuilder sql,
            List<Object> parameters
    ) {
        String quotedCol = dialect.quoteIdentifier(binary.column());
        Object value = binary.value();
        ComparisonOperator op = binary.operator();

        if (value == null && op == ComparisonOperator.EQUALS) {
            sql.append(quotedCol).append(" IS NULL");
            return;
        }
        if (value == null && op == ComparisonOperator.NOT_EQUALS) {
            sql.append(quotedCol).append(" IS NOT NULL");
            return;
        }

        String opSymbol = switch (op) {
            case EQUALS -> "=";
            case NOT_EQUALS -> "!=";
            case GREATER_THAN -> ">";
            case GREATER_THAN_OR_EQUAL -> ">=";
            case LESS_THAN -> "<";
            case LESS_THAN_OR_EQUAL -> "<=";
            case LIKE -> "LIKE";
            case ILIKE -> "ILIKE";
        };

        sql.append(quotedCol).append(" ").append(opSymbol).append(" ?");
        parameters.add(value);
    }

    private static void renderAnd(
            WherePredicate.AndPredicate and,
            SqlDialect dialect,
            StringBuilder sql,
            List<Object> parameters
    ) {
        List<WherePredicate> children = and.predicates();
        if (children.isEmpty()) {
            return;
        }
        if (children.size() == 1) {
            renderPredicate(children.getFirst(), dialect, sql, parameters);
            return;
        }
        sql.append("(");
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            renderPredicate(children.get(i), dialect, sql, parameters);
        }
        sql.append(")");
    }

    private static void renderOr(
            WherePredicate.OrPredicate or,
            SqlDialect dialect,
            StringBuilder sql,
            List<Object> parameters
    ) {
        List<WherePredicate> children = or.predicates();
        if (children.isEmpty()) {
            return;
        }
        if (children.size() == 1) {
            renderPredicate(children.getFirst(), dialect, sql, parameters);
            return;
        }
        sql.append("(");
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            renderPredicate(children.get(i), dialect, sql, parameters);
        }
        sql.append(")");
    }

    private static void renderNot(
            WherePredicate.NotPredicate not,
            SqlDialect dialect,
            StringBuilder sql,
            List<Object> parameters
    ) {
        sql.append("NOT (");
        renderPredicate(not.predicate(), dialect, sql, parameters);
        sql.append(")");
    }

    private static void renderIn(
            WherePredicate.InPredicate in,
            SqlDialect dialect,
            StringBuilder sql,
            List<Object> parameters
    ) {
        String quotedCol = dialect.quoteIdentifier(in.column());
        List<Object> values = in.values();
        if (values.isEmpty()) {
            sql.append(in.isNotIn() ? "1 = 1" : "1 = 0");
            return;
        }
        sql.append(quotedCol)
                .append(in.isNotIn() ? " NOT IN (" : " IN (");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
            parameters.add(values.get(i));
        }
        sql.append(")");
    }

    private static void renderIsNull(
            WherePredicate.IsNullPredicate isNull,
            SqlDialect dialect,
            StringBuilder sql
    ) {
        String quotedCol = dialect.quoteIdentifier(isNull.column());
        sql.append(quotedCol)
                .append(isNull.isNull() ? " IS NULL" : " IS NOT NULL");
    }

    private static void renderBetween(
            WherePredicate.BetweenPredicate between,
            SqlDialect dialect,
            StringBuilder sql,
            List<Object> parameters
    ) {
        String quotedCol = dialect.quoteIdentifier(between.column());
        sql.append(quotedCol).append(" BETWEEN ? AND ?");
        parameters.add(between.lower());
        parameters.add(between.upper());
    }
}

package com.graphorm.jdbc.builder;

import com.graphorm.core.query.ComparisonOperator;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds SQL WHERE clause expressions from {@link WherePredicate} trees.
 */
public final class WhereClauseBuilder {

    private WhereClauseBuilder() {}

    /**
     * Builds a parameterized SQL WHERE clause from a {@link WherePredicate}.
     *
     * @param predicate the predicate tree
     * @param dialect   the SQL dialect
     * @return the parameterized BoundSql
     */
    public static BoundSql build(WherePredicate predicate, SqlDialect dialect) {
        Objects.requireNonNull(predicate, "WherePredicate must not be null");
        Objects.requireNonNull(dialect, "SqlDialect must not be null");

        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>();
        renderPredicate(predicate, dialect, sql, parameters);
        return new BoundSql(sql.toString(), parameters);
    }

    /**
     * Extracts all runtime parameter values in order from a {@link WherePredicate} tree
     * without generating the SQL string.
     *
     * @param predicate the predicate
     * @return ordered list of parameter values
     */
    public static List<Object> extractParameters(WherePredicate predicate) {
        if (predicate == null) {
            return List.of();
        }
        List<Object> parameters = new ArrayList<>();
        collectParameters(predicate, parameters);
        return parameters;
    }

    private static void collectParameters(WherePredicate predicate, List<Object> parameters) {
        if (predicate instanceof WherePredicate.BinaryPredicate binary) {
            if (binary.value() == null && (binary.operator() == ComparisonOperator.EQUALS || binary.operator() == ComparisonOperator.NOT_EQUALS)) {
                return;
            }
            parameters.add(binary.value());
        } else if (predicate instanceof WherePredicate.RowComparisonPredicate rowComp) {
            parameters.addAll(rowComp.values());
        } else if (predicate instanceof WherePredicate.AndPredicate and) {
            for (WherePredicate child : and.predicates()) {
                collectParameters(child, parameters);
            }
        } else if (predicate instanceof WherePredicate.OrPredicate or) {
            for (WherePredicate child : or.predicates()) {
                collectParameters(child, parameters);
            }
        } else if (predicate instanceof WherePredicate.NotPredicate not) {
            collectParameters(not.predicate(), parameters);
        } else if (predicate instanceof WherePredicate.InPredicate in) {
            parameters.addAll(in.values());
        } else if (predicate instanceof WherePredicate.BetweenPredicate between) {
            parameters.add(between.lower());
            parameters.add(between.upper());
        } else if (predicate instanceof WherePredicate.RawPredicate raw) {
            parameters.addAll(raw.parameters());
        }
    }

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
            renderRaw(raw, sql, parameters);
        } else {
            throw new IllegalArgumentException("Unsupported predicate type: " + predicate.getClass().getName());
        }
    }

    private static void renderRaw(
            WherePredicate.RawPredicate raw,
            StringBuilder sql,
            List<Object> parameters
    ) {
        sql.append(raw.sql());
        parameters.addAll(raw.parameters());
    }

    private static void renderColumnComparison(
            WherePredicate.ColumnComparisonPredicate colComp,
            SqlDialect dialect,
            StringBuilder sql
    ) {
        String quotedLeft = dialect.quoteIdentifier(colComp.leftColumn());
        String quotedRight = dialect.quoteIdentifier(colComp.rightColumn());
        String opSymbol = switch (colComp.operator()) {
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

    public static final int MAX_IN_CHUNK_SIZE = 1000;

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

        if (values.size() <= MAX_IN_CHUNK_SIZE) {
            renderSingleIn(quotedCol, in.isNotIn(), values, sql, parameters);
        } else {
            // Partition large IN / NOT IN lists into chunks of MAX_IN_CHUNK_SIZE
            // For IN: (col IN (chunk1) OR col IN (chunk2))
            // For NOT IN: (col NOT IN (chunk1) AND col NOT IN (chunk2))
            boolean isNotIn = in.isNotIn();
            sql.append("(");
            int total = values.size();
            int chunkCount = (total + MAX_IN_CHUNK_SIZE - 1) / MAX_IN_CHUNK_SIZE;
            for (int c = 0; c < chunkCount; c++) {
                if (c > 0) {
                    sql.append(isNotIn ? " AND " : " OR ");
                }
                int from = c * MAX_IN_CHUNK_SIZE;
                int to = Math.min(from + MAX_IN_CHUNK_SIZE, total);
                List<Object> chunk = values.subList(from, to);
                renderSingleIn(quotedCol, isNotIn, chunk, sql, parameters);
            }
            sql.append(")");
        }
    }

    private static void renderSingleIn(
            String quotedCol,
            boolean isNotIn,
            List<Object> values,
            StringBuilder sql,
            List<Object> parameters
    ) {
        sql.append(quotedCol).append(isNotIn ? " NOT IN (" : " IN (");
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

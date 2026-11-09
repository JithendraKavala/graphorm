package com.graphorm.jdbc.cache;

import com.graphorm.core.query.CommonTableExpression;
import com.graphorm.core.query.ComparisonOperator;
import com.graphorm.core.query.GroupByClause;
import com.graphorm.core.query.JoinClause;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.ProjectedColumn;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.WherePredicate;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Utility for extracting canonical structural fingerprints and filter shapes from {@link SelectQuery} ASTs
 * without exposing or leaking runtime parameter values.
 */
public final class FilterShapeExtractor {

    private FilterShapeExtractor() {}

    /**
     * Produces a deterministic fingerprint of projected columns.
     *
     * @param columns list of projected columns
     * @return comma-separated column representation
     */
    public static String extractSelectionSetFingerprint(List<ProjectedColumn> columns) {
        if (columns == null || columns.isEmpty()) {
            return "*";
        }
        return columns.stream()
                .map(c -> c.expression() + (c.alias() != null ? ":" + c.alias() : ""))
                .sorted()
                .collect(Collectors.joining(","));
    }

    /**
     * Produces a structural shape of filters, joins, groupings, and pagination for the given query.
     *
     * @param query the select query AST
     * @return structural filter shape string
     */
    public static String extractFilterShape(SelectQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        StringBuilder sb = new StringBuilder();
        if (query.where() != null) {
            sb.append("WHERE[").append(predicateShape(query.where())).append("]");
        } else {
            sb.append("WHERE[ALL]");
        }

        if (!query.joins().isEmpty()) {
            sb.append(";JOINS[");
            for (int i = 0; i < query.joins().size(); i++) {
                if (i > 0) sb.append(",");
                JoinClause j = query.joins().get(i);
                sb.append(j.joinType()).append(":").append(j.table().identifier());
                if (j.condition() != null) {
                    sb.append(":ON(").append(predicateShape(j.condition())).append(")");
                }
            }
            sb.append("]");
        }

        if (!query.groupBy().isEmpty()) {
            sb.append(";GROUPBY[");
            for (int i = 0; i < query.groupBy().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(query.groupBy().get(i).expression());
            }
            sb.append("]");
        }

        if (query.having() != null && query.having().predicate() != null) {
            sb.append(";HAVING[").append(predicateShape(query.having().predicate())).append("]");
        }

        if (!query.orderBy().isEmpty()) {
            sb.append(";ORDER[");
            for (int i = 0; i < query.orderBy().size(); i++) {
                if (i > 0) sb.append(",");
                OrderByClause o = query.orderBy().get(i);
                sb.append(o.expression()).append(":").append(o.direction()).append(":").append(o.nullsOrder());
            }
            sb.append("]");
        }

        if (query.limit() != null) {
            sb.append(";LIMIT");
        }
        if (query.offset() != null) {
            sb.append(";OFFSET");
        }

        if (query.hasCtes()) {
            sb.append(";CTES[");
            for (int i = 0; i < query.ctes().size(); i++) {
                if (i > 0) sb.append(",");
                CommonTableExpression cte = query.ctes().get(i);
                sb.append(cte.name());
            }
            sb.append("]");
        }

        return sb.toString();
    }

    /**
     * Recursively computes the structural shape of a {@link WherePredicate} without literal parameter values.
     *
     * @param predicate the predicate
     * @return structural predicate shape
     */
    public static String predicateShape(WherePredicate predicate) {
        if (predicate == null) {
            return "ALL";
        }
        if (predicate instanceof WherePredicate.BinaryPredicate b) {
            if (b.value() == null && (b.operator() == ComparisonOperator.EQUALS || b.operator() == ComparisonOperator.NOT_EQUALS)) {
                return b.column() + ":" + (b.operator() == ComparisonOperator.EQUALS ? "IS_NULL" : "IS_NOT_NULL");
            }
            return b.column() + ":" + b.operator().name();
        } else if (predicate instanceof WherePredicate.ColumnComparisonPredicate cc) {
            return cc.leftColumn() + ":" + cc.operator().name() + ":" + cc.rightColumn();
        } else if (predicate instanceof WherePredicate.RowComparisonPredicate rc) {
            return "(" + String.join(",", rc.columns()) + "):" + rc.operator().name() + ":" + rc.values().size();
        } else if (predicate instanceof WherePredicate.AndPredicate and) {
            return "AND(" + and.predicates().stream().map(FilterShapeExtractor::predicateShape).collect(Collectors.joining(",")) + ")";
        } else if (predicate instanceof WherePredicate.OrPredicate or) {
            return "OR(" + or.predicates().stream().map(FilterShapeExtractor::predicateShape).collect(Collectors.joining(",")) + ")";
        } else if (predicate instanceof WherePredicate.NotPredicate not) {
            return "NOT(" + predicateShape(not.predicate()) + ")";
        } else if (predicate instanceof WherePredicate.InPredicate in) {
            return in.column() + ":" + (in.isNotIn() ? "NOT_IN" : "IN") + ":" + in.values().size();
        } else if (predicate instanceof WherePredicate.IsNullPredicate isNull) {
            return isNull.column() + ":" + (!isNull.isNull() ? "IS_NOT_NULL" : "IS_NULL");
        } else if (predicate instanceof WherePredicate.BetweenPredicate between) {
            return between.column() + ":BETWEEN";
        } else if (predicate instanceof WherePredicate.RawPredicate raw) {
            return "RAW(" + raw.sql() + ")";
        }
        return predicate.getClass().getSimpleName();
    }

    /**
     * Constructs a {@link QueryPlanKey} for the given query and dialect name.
     */
    public static QueryPlanKey createKey(SelectQuery query, String dialectName) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(dialectName, "dialectName must not be null");
        String entityName = query.source() != null ? query.source().identifier() : "";
        String selectionSet = extractSelectionSetFingerprint(query.columns());
        String filterShape = extractFilterShape(query);
        return QueryPlanKey.of(entityName, selectionSet, filterShape, dialectName);
    }
}

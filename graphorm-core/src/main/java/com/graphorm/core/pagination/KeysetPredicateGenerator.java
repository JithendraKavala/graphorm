package com.graphorm.core.pagination;

import com.graphorm.core.query.ComparisonOperator;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.OrderDirection;
import com.graphorm.core.query.WherePredicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generates keyset WHERE predicates for cursor-based pagination (e.g. {@code (created_at, id) > (?, ?)}).
 * Enforces deterministic ordering by appending a tie-breaker column (e.g. primary key 'id') if not already present.
 */
public final class KeysetPredicateGenerator {

    public static final String DEFAULT_TIE_BREAKER = "id";

    private KeysetPredicateGenerator() {}

    /**
     * Ensures deterministic ordering by appending the tie-breaker column if not already in the orderBy list.
     *
     * @param orderBy          the requested orderBy clauses
     * @param tieBreakerColumn the tie-breaker column (defaults to "id" if null/blank)
     * @return an immutable list of OrderByClause with the tie-breaker guaranteed
     */
    public static List<OrderByClause> ensureTieBreaker(List<OrderByClause> orderBy, String tieBreakerColumn) {
        String tieBreaker = (tieBreakerColumn != null && !tieBreakerColumn.isBlank())
                ? tieBreakerColumn.trim()
                : DEFAULT_TIE_BREAKER;

        List<OrderByClause> result = new ArrayList<>();
        if (orderBy != null) {
            result.addAll(orderBy);
        }

        boolean hasTieBreaker = result.stream()
                .anyMatch(c -> c.column().equalsIgnoreCase(tieBreaker)
                        || c.column().endsWith("." + tieBreaker)
                        || c.column().endsWith(".\"" + tieBreaker + "\""));

        if (!hasTieBreaker) {
            result.add(OrderByClause.asc(tieBreaker));
        }

        return List.copyOf(result);
    }

    /**
     * Generates a keyset {@link WherePredicate} for the given order clauses and cursor values.
     *
     * @param orderBy      the effective order by clauses
     * @param cursorValues the decoded cursor values corresponding to the order by columns
     * @param isForward    true for forward pagination ('after'), false for backward ('before')
     * @return the generated keyset {@link WherePredicate}
     */
    public static WherePredicate generatePredicate(
            List<OrderByClause> orderBy,
            List<Object> cursorValues,
            boolean isForward
    ) {
        Objects.requireNonNull(orderBy, "orderBy must not be null");
        Objects.requireNonNull(cursorValues, "cursorValues must not be null");

        if (orderBy.isEmpty() || cursorValues.isEmpty()) {
            throw new IllegalArgumentException("Cannot generate keyset predicate with empty orderBy or cursorValues");
        }

        int count = Math.min(orderBy.size(), cursorValues.size());
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        List<OrderDirection> directions = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            columns.add(orderBy.get(i).column());
            values.add(cursorValues.get(i));
            directions.add(orderBy.get(i).direction());
        }

        // Check if all directions are uniform
        boolean allAsc = directions.stream().allMatch(d -> d == OrderDirection.ASC);
        boolean allDesc = directions.stream().allMatch(d -> d == OrderDirection.DESC);

        if (allAsc || allDesc) {
            ComparisonOperator op;
            if (allAsc) {
                op = isForward ? ComparisonOperator.GREATER_THAN : ComparisonOperator.LESS_THAN;
            } else {
                op = isForward ? ComparisonOperator.LESS_THAN : ComparisonOperator.GREATER_THAN;
            }

            if (count == 1) {
                return new WherePredicate.BinaryPredicate(columns.getFirst(), op, values.getFirst());
            } else {
                return WherePredicate.rowComparison(columns, op, values);
            }
        }

        // Mixed directions: expand into composite boolean predicates
        return buildMixedPredicate(columns, values, directions, isForward, 0);
    }

    private static WherePredicate buildMixedPredicate(
            List<String> columns,
            List<Object> values,
            List<OrderDirection> directions,
            boolean isForward,
            int index
    ) {
        String col = columns.get(index);
        Object val = values.get(index);
        OrderDirection dir = directions.get(index);

        ComparisonOperator op = (dir == OrderDirection.ASC)
                ? (isForward ? ComparisonOperator.GREATER_THAN : ComparisonOperator.LESS_THAN)
                : (isForward ? ComparisonOperator.LESS_THAN : ComparisonOperator.GREATER_THAN);

        WherePredicate direct = new WherePredicate.BinaryPredicate(col, op, val);

        if (index == columns.size() - 1) {
            return direct;
        }

        WherePredicate eq = WherePredicate.eq(col, val);
        WherePredicate next = buildMixedPredicate(columns, values, directions, isForward, index + 1);

        return direct.or(eq.and(next));
    }
}

package com.graphorm.core.query;

import java.util.Objects;

/**
 * Strongly typed representation of an individual ordering criterion in an ORDER BY clause.
 *
 * @param expression the column name, alias, or expression to sort by
 * @param direction  sort direction (ASC, DESC)
 * @param nullsOrder null placement (FIRST, LAST, DEFAULT)
 */
public record OrderByClause(
        String expression,
        OrderDirection direction,
        NullsOrder nullsOrder
) {

    public OrderByClause {
        Objects.requireNonNull(expression, "Order by expression must not be null");
        if (expression.isBlank()) {
            throw new IllegalArgumentException("Order by expression must not be blank");
        }
        if (direction == null) {
            direction = OrderDirection.ASC;
        }
        if (nullsOrder == null) {
            nullsOrder = NullsOrder.DEFAULT;
        }
    }

    /**
     * Alias for expression, representing the target sorted column or expression.
     */
    public String column() {
        return expression;
    }

    public static OrderByClause of(String expression, OrderDirection direction) {
        return new OrderByClause(expression, direction, NullsOrder.DEFAULT);
    }

    public static OrderByClause of(String expression, OrderDirection direction, NullsOrder nullsOrder) {
        return new OrderByClause(expression, direction, nullsOrder);
    }

    public static OrderByClause asc(String expression) {
        return new OrderByClause(expression, OrderDirection.ASC, NullsOrder.DEFAULT);
    }

    public static OrderByClause desc(String expression) {
        return new OrderByClause(expression, OrderDirection.DESC, NullsOrder.DEFAULT);
    }

    public static OrderByClause asc(String expression, NullsOrder nullsOrder) {
        return new OrderByClause(expression, OrderDirection.ASC, nullsOrder);
    }

    public static OrderByClause desc(String expression, NullsOrder nullsOrder) {
        return new OrderByClause(expression, OrderDirection.DESC, nullsOrder);
    }

    public static OrderByClause ascNullsFirst(String expression) {
        return new OrderByClause(expression, OrderDirection.ASC, NullsOrder.FIRST);
    }

    public static OrderByClause ascNullsLast(String expression) {
        return new OrderByClause(expression, OrderDirection.ASC, NullsOrder.LAST);
    }

    public static OrderByClause descNullsFirst(String expression) {
        return new OrderByClause(expression, OrderDirection.DESC, NullsOrder.FIRST);
    }

    public static OrderByClause descNullsLast(String expression) {
        return new OrderByClause(expression, OrderDirection.DESC, NullsOrder.LAST);
    }
}

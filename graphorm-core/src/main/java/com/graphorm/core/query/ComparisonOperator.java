package com.graphorm.core.query;

/**
 * SQL comparison operators supported in predicates.
 */
public enum ComparisonOperator {
    EQUALS("="),
    NOT_EQUALS("<>"),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL(">="),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<="),
    LIKE("LIKE"),
    ILIKE("ILIKE");

    private final String symbol;

    ComparisonOperator(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Returns the SQL representation of this operator.
     *
     * @return the SQL symbol
     */
    public String symbol() {
        return symbol;
    }
}

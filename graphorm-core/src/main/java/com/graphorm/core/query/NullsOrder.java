package com.graphorm.core.query;

/**
 * Null value placement in SQL ORDER BY clauses.
 */
public enum NullsOrder {
    /**
     * Default dialect behavior.
     */
    DEFAULT,

    /**
     * Null values appear first.
     */
    FIRST,

    /**
     * Null values appear last.
     */
    LAST
}

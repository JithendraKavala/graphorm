package com.graphorm.core.query;

/**
 * SQL join types supported in query planning and view definitions.
 */
public enum JoinType {
    /**
     * Inner join.
     */
    INNER("INNER JOIN"),

    /**
     * Left outer join.
     */
    LEFT("LEFT JOIN"),

    /**
     * Right outer join.
     */
    RIGHT("RIGHT JOIN"),

    /**
     * Full outer join.
     */
    FULL("FULL JOIN");

    private final String sqlKeyword;

    JoinType(String sqlKeyword) {
        this.sqlKeyword = sqlKeyword;
    }

    /**
     * Returns the standard SQL keyword for this join type.
     *
     * @return the SQL keyword representation
     */
    public String sqlKeyword() {
        return sqlKeyword;
    }
}

package com.graphorm.core.query;

/**
 * Types of SQL operations represented in the Query AST.
 */
public enum QueryType {
    /**
     * SELECT read query.
     */
    SELECT,

    /**
     * INSERT write query.
     */
    INSERT,

    /**
     * UPDATE write query.
     */
    UPDATE,

    /**
     * DELETE write query.
     */
    DELETE
}

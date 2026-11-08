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
    DELETE,

    /**
     * UPSERT (INSERT ... ON CONFLICT) write query.
     */
    UPSERT,

    /**
     * Multi-row batch INSERT write query.
     */
    BATCH_INSERT,

    /**
     * Bulk UPDATE write query.
     */
    BULK_UPDATE
}

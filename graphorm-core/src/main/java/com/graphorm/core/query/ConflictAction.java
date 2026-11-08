package com.graphorm.core.query;

/**
 * Action to perform when an insert conflict occurs during an upsert operation.
 */
public enum ConflictAction {
    /**
     * PostgreSQL ON CONFLICT DO NOTHING - ignores the conflicting insert.
     */
    DO_NOTHING,

    /**
     * PostgreSQL ON CONFLICT DO UPDATE SET ... - updates existing record with new values.
     */
    DO_UPDATE
}

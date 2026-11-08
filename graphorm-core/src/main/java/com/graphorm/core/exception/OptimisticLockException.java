package com.graphorm.core.exception;

import java.io.Serial;

/**
 * Thrown when an optimistic locking check fails during an UPDATE operation because the entity
 * has been modified or deleted concurrently by another transaction (0 rows affected).
 */
public class OptimisticLockException extends DataAccessException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String entityName;
    private final transient Object identifier;
    private final transient Object expectedVersion;

    public OptimisticLockException(String message) {
        this(message, null, null, null, null, null);
    }

    public OptimisticLockException(String message, String sql) {
        this(message, sql, null, null, null, null);
    }

    public OptimisticLockException(String message, String sql, String entityName, Object identifier, Object expectedVersion, Throwable cause) {
        super(message, sql, "OPTIMISTIC_LOCK_FAILURE", cause);
        this.entityName = entityName;
        this.identifier = identifier;
        this.expectedVersion = expectedVersion;
    }

    public String entityName() {
        return entityName;
    }

    public Object identifier() {
        return identifier;
    }

    public Object expectedVersion() {
        return expectedVersion;
    }
}

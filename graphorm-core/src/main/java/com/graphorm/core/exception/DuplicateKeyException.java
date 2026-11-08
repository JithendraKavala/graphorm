package com.graphorm.core.exception;

import java.io.Serial;

/**
 * Thrown when an insert or update violates a unique constraint or primary key (e.g. PostgreSQL error 23505).
 */
public class DuplicateKeyException extends DataIntegrityViolationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DuplicateKeyException(String message) {
        super(message);
    }

    public DuplicateKeyException(String message, Throwable cause) {
        super(message, cause);
    }

    public DuplicateKeyException(String message, String sql, Throwable cause) {
        super(message, sql, cause);
    }

    public DuplicateKeyException(String message, String sql, String sqlState, Throwable cause) {
        super(message, sql, sqlState, cause);
    }
}

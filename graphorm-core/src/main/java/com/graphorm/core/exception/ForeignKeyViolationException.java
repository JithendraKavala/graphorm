package com.graphorm.core.exception;

import java.io.Serial;

/**
 * Thrown when an insert, update, or delete violates a foreign key constraint (e.g. PostgreSQL error 23503).
 */
public class ForeignKeyViolationException extends DataIntegrityViolationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ForeignKeyViolationException(String message) {
        super(message);
    }

    public ForeignKeyViolationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ForeignKeyViolationException(String message, String sql, Throwable cause) {
        super(message, sql, cause);
    }

    public ForeignKeyViolationException(String message, String sql, String sqlState, Throwable cause) {
        super(message, sql, sqlState, cause);
    }
}

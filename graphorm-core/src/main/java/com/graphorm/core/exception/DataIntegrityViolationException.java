package com.graphorm.core.exception;

import java.io.Serial;

/**
 * Thrown when an SQL operation violates a database integrity constraint (unique, foreign key, check, not null).
 */
public class DataIntegrityViolationException extends DataAccessException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DataIntegrityViolationException(String message) {
        super(message);
    }

    public DataIntegrityViolationException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataIntegrityViolationException(String message, String sql, Throwable cause) {
        super(message, sql, cause);
    }

    public DataIntegrityViolationException(String message, String sql, String sqlState, Throwable cause) {
        super(message, sql, sqlState, cause);
    }
}

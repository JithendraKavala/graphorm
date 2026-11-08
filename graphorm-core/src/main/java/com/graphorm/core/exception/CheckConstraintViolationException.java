package com.graphorm.core.exception;

import java.io.Serial;

/**
 * Thrown when an insert or update violates a table or column check constraint (e.g. PostgreSQL error 23514).
 */
public class CheckConstraintViolationException extends DataIntegrityViolationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CheckConstraintViolationException(String message) {
        super(message);
    }

    public CheckConstraintViolationException(String message, Throwable cause) {
        super(message, cause);
    }

    public CheckConstraintViolationException(String message, String sql, Throwable cause) {
        super(message, sql, cause);
    }

    public CheckConstraintViolationException(String message, String sql, String sqlState, Throwable cause) {
        super(message, sql, sqlState, cause);
    }
}

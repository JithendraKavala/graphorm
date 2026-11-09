package com.graphorm.core.exception;

import java.io.Serial;

/**
 * Exception thrown when a concurrency conflict occurs, such as serialization failures,
 * deadlock aborts, or lock acquisition timeouts.
 */
public class ConcurrencyFailureException extends DataAccessException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConcurrencyFailureException(String message) {
        super(message);
    }

    public ConcurrencyFailureException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConcurrencyFailureException(String message, String sql, Throwable cause) {
        super(message, sql, cause);
    }

    public ConcurrencyFailureException(String message, String sql, String sqlState, Throwable cause) {
        super(message, sql, sqlState, cause);
    }
}

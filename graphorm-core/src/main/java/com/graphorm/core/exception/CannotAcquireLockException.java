package com.graphorm.core.exception;

import java.io.Serial;

/**
 * Exception thrown when a database lock (row lock, table lock, or advisory lock) cannot be acquired or times out.
 */
public class CannotAcquireLockException extends ConcurrencyFailureException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CannotAcquireLockException(String message) {
        super(message);
    }

    public CannotAcquireLockException(String message, Throwable cause) {
        super(message, cause);
    }

    public CannotAcquireLockException(String message, String sql, Throwable cause) {
        super(message, sql, cause);
    }

    public CannotAcquireLockException(String message, String sql, String sqlState, Throwable cause) {
        super(message, sql, sqlState, cause);
    }
}

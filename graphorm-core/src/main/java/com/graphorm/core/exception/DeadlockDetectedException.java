package com.graphorm.core.exception;

import java.io.Serial;

/**
 * Exception thrown when a database transaction is aborted due to a detected deadlock.
 */
public class DeadlockDetectedException extends ConcurrencyFailureException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DeadlockDetectedException(String message) {
        super(message);
    }

    public DeadlockDetectedException(String message, Throwable cause) {
        super(message, cause);
    }

    public DeadlockDetectedException(String message, String sql, Throwable cause) {
        super(message, sql, cause);
    }

    public DeadlockDetectedException(String message, String sql, String sqlState, Throwable cause) {
        super(message, sql, sqlState, cause);
    }
}

package com.graphorm.jdbc.exception;

import com.graphorm.core.exception.DataAccessException;

import java.io.Serial;

/**
 * Exception thrown when a database query execution or JDBC resource operation fails.
 */
public class QueryExecutionException extends DataAccessException {

    @Serial
    private static final long serialVersionUID = 1L;

    public QueryExecutionException(String message) {
        super(message);
    }

    public QueryExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public QueryExecutionException(String message, String sql, Throwable cause) {
        super(message, sql, cause);
    }

    public QueryExecutionException(String message, String sql, String sqlState, Throwable cause) {
        super(message, sql, sqlState, cause);
    }
}

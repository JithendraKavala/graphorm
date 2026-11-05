package com.graphorm.jdbc.exception;

import com.graphorm.core.exception.GraphOrmException;

import java.io.Serial;

/**
 * Exception thrown when a database query execution or JDBC resource operation fails.
 */
public class QueryExecutionException extends GraphOrmException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String sql;

    public QueryExecutionException(String message) {
        super(message);
        this.sql = null;
    }

    public QueryExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.sql = null;
    }

    public QueryExecutionException(String message, String sql, Throwable cause) {
        super(message, cause);
        this.sql = sql;
    }

    /**
     * Returns the SQL statement that caused the failure, if available.
     *
     * @return the SQL string, or null
     */
    public String sql() {
        return sql;
    }
}

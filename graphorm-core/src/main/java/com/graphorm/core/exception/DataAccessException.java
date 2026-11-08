package com.graphorm.core.exception;

import java.io.Serial;

/**
 * Root unchecked exception for all data access and database execution errors.
 * Provides access to the underlying SQL statement and SQLSTATE error code if available.
 */
public class DataAccessException extends GraphOrmException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String sql;
    private final String sqlState;

    public DataAccessException(String message) {
        this(message, (String) null, (String) null, null);
    }

    public DataAccessException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    public DataAccessException(String message, String sql, Throwable cause) {
        this(message, sql, null, cause);
    }

    public DataAccessException(String message, String sql, String sqlState, Throwable cause) {
        super(sqlState != null ? "DATA_ACCESS_" + sqlState : "DATA_ACCESS_ERROR", message, cause);
        this.sql = sql;
        this.sqlState = sqlState;
    }

    /**
     * Returns the SQL statement that caused the failure, or {@code null} if not applicable.
     */
    public String sql() {
        return sql;
    }

    /**
     * Returns the SQL statement that caused the failure, or {@code null} if not applicable.
     */
    public String getSql() {
        return sql;
    }

    /**
     * Returns the SQLSTATE code returned by the database, or {@code null} if unavailable.
     */
    public String sqlState() {
        return sqlState;
    }

    /**
     * Returns the SQLSTATE code returned by the database, or {@code null} if unavailable.
     */
    public String getSqlState() {
        return sqlState;
    }
}

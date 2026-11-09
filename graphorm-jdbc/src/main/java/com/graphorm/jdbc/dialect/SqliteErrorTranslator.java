package com.graphorm.jdbc.dialect;

import com.graphorm.core.exception.CannotAcquireLockException;
import com.graphorm.core.exception.CheckConstraintViolationException;
import com.graphorm.core.exception.DataAccessException;
import com.graphorm.core.exception.DataIntegrityViolationException;
import com.graphorm.core.exception.DuplicateKeyException;
import com.graphorm.core.exception.ForeignKeyViolationException;
import com.graphorm.jdbc.exception.QueryExecutionException;

import java.sql.SQLException;

/**
 * Translates SQLite {@link SQLException} instances into typed GraphORM exceptions
 * based on SQLite error codes (e.g. SQLITE_CONSTRAINT = 19) and message parsing.
 * <ul>
 *     <li>SQLITE_BUSY (5) / SQLITE_LOCKED (6): {@link CannotAcquireLockException}</li>
 *     <li>UNIQUE / PRIMARY KEY constraint: {@link DuplicateKeyException}</li>
 *     <li>FOREIGN KEY constraint: {@link ForeignKeyViolationException}</li>
 *     <li>CHECK constraint: {@link CheckConstraintViolationException}</li>
 *     <li>NOT NULL constraint: {@link DataIntegrityViolationException}</li>
 *     <li>Other: {@link QueryExecutionException}</li>
 * </ul>
 */
public class SqliteErrorTranslator implements SqlErrorTranslator {

    @Override
    public DataAccessException translate(String task, String sql, SQLException ex) {
        if (ex == null) {
            return new QueryExecutionException((task != null ? task : "Database error") + ": unknown error", sql, null);
        }

        int errorCode = ex.getErrorCode();
        String sqlState = ex.getSQLState();
        String rawMsg = ex.getMessage() != null ? ex.getMessage() : "";
        String message = (task != null ? task + " failed: " : "") + rawMsg;

        // 1. Concurrency / lock errors
        if (errorCode == 5 || rawMsg.contains("database is locked")) {
            return new CannotAcquireLockException(message, sql, sqlState, ex);
        }
        if (errorCode == 6 || rawMsg.contains("database table is locked")) {
            return new CannotAcquireLockException(message, sql, sqlState, ex);
        }

        // 2. Inspect message heuristics (SQLite error details are rich in message text)
        if (rawMsg.contains("UNIQUE constraint failed") || rawMsg.contains("PRIMARY KEY must be unique")) {
            return new DuplicateKeyException(message, sql, sqlState, ex);
        }
        if (rawMsg.contains("FOREIGN KEY constraint failed")) {
            return new ForeignKeyViolationException(message, sql, sqlState, ex);
        }
        if (rawMsg.contains("CHECK constraint failed")) {
            return new CheckConstraintViolationException(message, sql, sqlState, ex);
        }
        if (rawMsg.contains("NOT NULL constraint failed")) {
            return new DataIntegrityViolationException(message, sql, sqlState, ex);
        }

        // 3. Extended vendor error codes (SQLITE_CONSTRAINT variants)
        if (errorCode == 1555 || errorCode == 2067) {
            // SQLITE_CONSTRAINT_PRIMARYKEY / SQLITE_CONSTRAINT_UNIQUE
            return new DuplicateKeyException(message, sql, sqlState, ex);
        }
        if (errorCode == 787) {
            // SQLITE_CONSTRAINT_FOREIGNKEY
            return new ForeignKeyViolationException(message, sql, sqlState, ex);
        }
        if (errorCode == 275) {
            // SQLITE_CONSTRAINT_CHECK
            return new CheckConstraintViolationException(message, sql, sqlState, ex);
        }
        if (errorCode == 1299) {
            // SQLITE_CONSTRAINT_NOTNULL
            return new DataIntegrityViolationException(message, sql, sqlState, ex);
        }
        if (errorCode == 19 || (errorCode & 0xFF) == 19) {
            return new DataIntegrityViolationException(message, sql, sqlState, ex);
        }

        // 4. SQLSTATE 23*
        if (sqlState != null && sqlState.startsWith("23")) {
            return new DataIntegrityViolationException(message, sql, sqlState, ex);
        }

        return new QueryExecutionException(message, sql, sqlState, ex);
    }
}

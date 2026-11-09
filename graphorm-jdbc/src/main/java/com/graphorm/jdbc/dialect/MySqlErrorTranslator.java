package com.graphorm.jdbc.dialect;

import com.graphorm.core.exception.CannotAcquireLockException;
import com.graphorm.core.exception.CheckConstraintViolationException;
import com.graphorm.core.exception.ConcurrencyFailureException;
import com.graphorm.core.exception.DataAccessException;
import com.graphorm.core.exception.DataIntegrityViolationException;
import com.graphorm.core.exception.DeadlockDetectedException;
import com.graphorm.core.exception.DuplicateKeyException;
import com.graphorm.core.exception.ForeignKeyViolationException;
import com.graphorm.jdbc.exception.QueryExecutionException;

import java.sql.SQLException;

/**
 * Translates MySQL {@link SQLException} instances into typed GraphORM exceptions
 * based on MySQL vendor error codes and ANSI-92/SQLSTATE codes.
 * <ul>
 *     <li>Error 1062 / Duplicate entry: {@link DuplicateKeyException}</li>
 *     <li>Error 1216, 1217, 1451, 1452: {@link ForeignKeyViolationException}</li>
 *     <li>Error 3819, 4025: {@link CheckConstraintViolationException}</li>
 *     <li>Error 1048, 1364 or SQLSTATE 23*: {@link DataIntegrityViolationException}</li>
 *     <li>Error 1205, 3572: {@link CannotAcquireLockException}</li>
 *     <li>Error 1213: {@link DeadlockDetectedException}</li>
 *     <li>Other: {@link QueryExecutionException}</li>
 * </ul>
 */
public class MySqlErrorTranslator implements SqlErrorTranslator {

    @Override
    public DataAccessException translate(String task, String sql, SQLException ex) {
        if (ex == null) {
            return new QueryExecutionException((task != null ? task : "Database error") + ": unknown error", sql, null);
        }

        int errorCode = ex.getErrorCode();
        String sqlState = ex.getSQLState();
        String message = (task != null ? task + " failed: " : "") + ex.getMessage();

        // 1. Check MySQL vendor error codes
        switch (errorCode) {
            case 1062:
                return new DuplicateKeyException(message, sql, sqlState, ex);
            case 1216:
            case 1217:
            case 1451:
            case 1452:
                return new ForeignKeyViolationException(message, sql, sqlState, ex);
            case 3819:
            case 4025:
                return new CheckConstraintViolationException(message, sql, sqlState, ex);
            case 1048:
            case 1364:
                return new DataIntegrityViolationException(message, sql, sqlState, ex);
            case 1205:
            case 3572:
                return new CannotAcquireLockException(message, sql, sqlState, ex);
            case 1213:
                return new DeadlockDetectedException(message, sql, sqlState, ex);
            default:
                break;
        }

        // 2. Check message content heuristics
        String rawMsg = ex.getMessage() != null ? ex.getMessage() : "";
        if (rawMsg.contains("Duplicate entry") || rawMsg.contains("duplicate key")) {
            return new DuplicateKeyException(message, sql, sqlState, ex);
        }
        if (rawMsg.contains("a foreign key constraint fails")) {
            return new ForeignKeyViolationException(message, sql, sqlState, ex);
        }
        if (rawMsg.contains("Check constraint") && rawMsg.contains("is violated")) {
            return new CheckConstraintViolationException(message, sql, sqlState, ex);
        }
        if (rawMsg.contains("Lock wait timeout exceeded")) {
            return new CannotAcquireLockException(message, sql, sqlState, ex);
        }
        if (rawMsg.contains("Deadlock found")) {
            return new DeadlockDetectedException(message, sql, sqlState, ex);
        }

        // 3. Check SQLSTATE
        if (sqlState != null) {
            if ("40001".equals(sqlState)) {
                return new ConcurrencyFailureException(message, sql, sqlState, ex);
            }
            if ("40P01".equals(sqlState)) {
                return new DeadlockDetectedException(message, sql, sqlState, ex);
            }
            if ("23000".equals(sqlState)) {
                return new DataIntegrityViolationException(message, sql, sqlState, ex);
            }
            if (sqlState.startsWith("23")) {
                return new DataIntegrityViolationException(message, sql, sqlState, ex);
            }
        }

        return new QueryExecutionException(message, sql, sqlState, ex);
    }
}

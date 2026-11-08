package com.graphorm.jdbc.dialect;

import com.graphorm.core.exception.CheckConstraintViolationException;
import com.graphorm.core.exception.DataAccessException;
import com.graphorm.core.exception.DataIntegrityViolationException;
import com.graphorm.core.exception.DuplicateKeyException;
import com.graphorm.core.exception.ForeignKeyViolationException;
import com.graphorm.jdbc.exception.QueryExecutionException;

import java.sql.SQLException;

/**
 * Translates PostgreSQL {@link SQLException} instances into typed GraphORM exceptions
 * based on standard PostgreSQL SQLSTATE codes.
 * <ul>
 *     <li>{@code 23505}: {@link DuplicateKeyException} (unique_violation)</li>
 *     <li>{@code 23503}: {@link ForeignKeyViolationException} (foreign_key_violation)</li>
 *     <li>{@code 23514}: {@link CheckConstraintViolationException} (check_violation)</li>
 *     <li>{@code 23*}: {@link DataIntegrityViolationException} (integrity_constraint_violation)</li>
 *     <li>Other: {@link QueryExecutionException}</li>
 * </ul>
 */
public class PostgreSqlErrorTranslator implements SqlErrorTranslator {

    @Override
    public DataAccessException translate(String task, String sql, SQLException ex) {
        if (ex == null) {
            return new QueryExecutionException((task != null ? task : "Database error") + ": unknown error", sql, null);
        }

        String sqlState = ex.getSQLState();
        String message = (task != null ? task + " failed: " : "") + ex.getMessage();

        if (sqlState != null) {
            return switch (sqlState) {
                case "23505" -> new DuplicateKeyException(message, sql, sqlState, ex);
                case "23503" -> new ForeignKeyViolationException(message, sql, sqlState, ex);
                case "23514" -> new CheckConstraintViolationException(message, sql, sqlState, ex);
                default -> {
                    if (sqlState.startsWith("23")) {
                        yield new DataIntegrityViolationException(message, sql, sqlState, ex);
                    }
                    yield new QueryExecutionException(message, sql, sqlState, ex);
                }
            };
        }

        return new QueryExecutionException(message, sql, ex);
    }
}

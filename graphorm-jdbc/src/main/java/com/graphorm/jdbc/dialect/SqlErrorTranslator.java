package com.graphorm.jdbc.dialect;

import com.graphorm.core.exception.DataAccessException;

import java.sql.SQLException;

/**
 * Strategy interface for translating low-level {@link SQLException} instances
 * into typed, portable {@link DataAccessException} hierarchies.
 */
@FunctionalInterface
public interface SqlErrorTranslator {

    /**
     * Translates a {@link SQLException} into a typed {@link DataAccessException}.
     *
     * @param task description of the operation being attempted (e.g. "Query execution", "Update execution")
     * @param sql  the SQL statement being executed, or null
     * @param ex   the underlying SQLException
     * @return a typed DataAccessException
     */
    DataAccessException translate(String task, String sql, SQLException ex);
}

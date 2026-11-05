package com.graphorm.jdbc.transaction;

import com.graphorm.jdbc.exception.QueryExecutionException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Basic programmatic JDBC transaction manager providing safe transaction boundaries,
 * automatic rollback upon failures, and zero resource leaks without external framework dependencies.
 */
public class JdbcTransactionManager {

    private final DataSource dataSource;

    public JdbcTransactionManager(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource must not be null");
    }

    /**
     * Executes the given callback inside a managed transaction.
     *
     * @param callback the action to execute
     * @param <T>      the return type
     * @return the computation result
     * @throws QueryExecutionException if a database error occurs during the transaction
     */
    public <T> T executeInTransaction(TransactionCallback<T> callback) {
        Objects.requireNonNull(callback, "TransactionCallback must not be null");
        try (Connection conn = dataSource.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                T result = callback.doInTransaction(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new QueryExecutionException("Transaction failed and was rolled back", e);
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignored) {
                    // Ignore restore error on closed connection
                }
            }
        } catch (SQLException e) {
            throw new QueryExecutionException("Failed to acquire connection for transaction", e);
        }
    }
}

package com.graphorm.jdbc.transaction;

import java.sql.Connection;

/**
 * Functional callback executed within a transactional connection boundary.
 *
 * @param <T> the result type
 */
@FunctionalInterface
public interface TransactionCallback<T> {

    /**
     * Executes logic within the active transaction.
     *
     * @param connection the active JDBC connection with autoCommit set to false
     * @return the computation result
     * @throws Exception if any error occurs causing transaction rollback
     */
    T doInTransaction(Connection connection) throws Exception;
}

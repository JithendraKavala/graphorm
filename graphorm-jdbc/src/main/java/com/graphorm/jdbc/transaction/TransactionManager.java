package com.graphorm.jdbc.transaction;

/**
 * Pluggable SPI for executing operations within a transaction boundary (ADR-014).
 */
public interface TransactionManager {

    /**
     * Executes the given callback inside a managed transaction.
     *
     * @param callback the action to execute
     * @param <T>      the return type
     * @return the computation result
     */
    <T> T executeInTransaction(TransactionCallback<T> callback);

    /**
     * Executes a runnable inside a managed transaction.
     *
     * @param action the action to execute
     */
    default void executeInTransaction(Runnable action) {
        executeInTransaction(conn -> {
            action.run();
            return null;
        });
    }
}

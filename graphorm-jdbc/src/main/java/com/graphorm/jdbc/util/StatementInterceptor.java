package com.graphorm.jdbc.util;

import java.util.List;

/**
 * Interceptor hook invoked before and after SQL statement execution.
 */
public interface StatementInterceptor {

    /**
     * Called immediately before a SQL statement is executed.
     *
     * @param sql        the SQL query string
     * @param parameters positional parameter values bound to the statement
     */
    default void beforeExecute(String sql, List<Object> parameters) {}

    /**
     * Called immediately after a SQL statement has finished execution.
     *
     * @param sql           the SQL query string
     * @param parameters    positional parameter values bound to the statement
     * @param durationNanos execution time in nanoseconds
     */
    default void afterExecute(String sql, List<Object> parameters, long durationNanos) {}
}

package com.graphorm.core.query;

/**
 * Root contract for all strongly typed SQL Query AST representations in GraphORM.
 */
public interface SqlQuery {

    /**
     * Returns the operational type of this SQL query.
     *
     * @return the query type (SELECT, INSERT, UPDATE, or DELETE)
     */
    QueryType queryType();
}

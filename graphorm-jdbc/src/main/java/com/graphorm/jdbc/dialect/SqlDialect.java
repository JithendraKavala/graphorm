package com.graphorm.jdbc.dialect;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.jdbc.executor.BoundSql;

import java.util.List;

/**
 * Abstraction isolating database-specific SQL dialect formatting, identifier quoting,
 * pagination, aggregation functions, type conversions, and CRUD query building.
 */
public interface SqlDialect {

    /**
     * Quotes a database identifier (table name, column name, or alias) to prevent SQL injection
     * and allow reserved keywords.
     *
     * @param identifier the identifier to quote
     * @return the safely quoted identifier
     */
    String quoteIdentifier(String identifier);

    /**
     * Formats pagination SQL syntax using LIMIT and OFFSET clauses with positional '?' placeholders.
     *
     * @param limit  the maximum rows to return (may be null)
     * @param offset the number of rows to skip (may be null)
     * @return the SQL pagination snippet (e.g. "LIMIT ? OFFSET ?"), or empty string if neither is set
     */
    String formatLimitOffset(Integer limit, Integer offset);

    /**
     * Formats pagination SQL syntax using integer limit and offset.
     *
     * @param limit  the maximum rows to return
     * @param offset the number of rows to skip
     * @return the SQL pagination snippet
     */
    default String formatLimitOffset(int limit, int offset) {
        return formatLimitOffset(Integer.valueOf(limit), Integer.valueOf(offset));
    }

    /**
     * Builds a parameterized {@link BoundSql} for a {@link SelectQuery}.
     *
     * @param query the select query AST
     * @return the parameterized BoundSql
     */
    BoundSql buildSelectSql(SelectQuery query);

    /**
     * Alias for {@link #buildSelectSql(SelectQuery)}.
     *
     * @param query the select query AST
     * @return the parameterized BoundSql
     */
    default BoundSql buildSelect(SelectQuery query) {
        return buildSelectSql(query);
    }

    /**
     * Builds a parameterized {@link BoundSql} for an {@link InsertQuery}.
     *
     * @param query the insert query AST
     * @return the parameterized BoundSql
     */
    BoundSql buildInsertSql(InsertQuery query);

    /**
     * Alias for {@link #buildInsertSql(InsertQuery)}.
     *
     * @param query the insert query AST
     * @return the parameterized BoundSql
     */
    default BoundSql buildInsert(InsertQuery query) {
        return buildInsertSql(query);
    }

    /**
     * Builds a parameterized {@link BoundSql} for an {@link UpdateQuery}.
     *
     * @param query the update query AST
     * @return the parameterized BoundSql
     */
    BoundSql buildUpdateSql(UpdateQuery query);

    /**
     * Alias for {@link #buildUpdateSql(UpdateQuery)}.
     *
     * @param query the update query AST
     * @return the parameterized BoundSql
     */
    default BoundSql buildUpdate(UpdateQuery query) {
        return buildUpdateSql(query);
    }

    /**
     * Builds a parameterized {@link BoundSql} for a {@link DeleteQuery}.
     *
     * @param query the delete query AST
     * @return the parameterized BoundSql
     */
    BoundSql buildDeleteSql(DeleteQuery query);

    /**
     * Alias for {@link #buildDeleteSql(DeleteQuery)}.
     *
     * @param query the delete query AST
     * @return the parameterized BoundSql
     */
    default BoundSql buildDelete(DeleteQuery query) {
        return buildDeleteSql(query);
    }

    /**
     * Formats an aggregation expression into SQL for this dialect.
     *
     * @param type       the aggregation type (COUNT, SUM, AVG, MIN, MAX)
     * @param expression the column or SQL expression to aggregate
     * @return the formatted aggregation SQL expression
     */
    String buildAggregateSql(AggregateType type, String expression);

    /**
     * Alias for {@link #buildAggregateSql(AggregateType, String)}.
     *
     * @param type       the aggregation type
     * @param expression the column or SQL expression
     * @return the formatted aggregation SQL expression
     */
    default String aggregateFunction(AggregateType type, String expression) {
        return buildAggregateSql(type, expression);
    }

    /**
     * Converts a Java domain value into a database-compatible value before JDBC binding.
     *
     * @param value the Java value
     * @param field the field metadata (may be null)
     * @return the database-compatible value
     */
    Object convertToDatabaseValue(Object value, FieldMetadata field);

    /**
     * Converts a raw JDBC database value into the corresponding Java domain value.
     *
     * @param dbValue the database value
     * @param field   the field metadata
     * @return the converted Java value
     */
    Object convertFromDatabaseValue(Object dbValue, FieldMetadata field);

    /**
     * Indicates whether this dialect natively supports the RETURNING clause on INSERT/UPDATE.
     *
     * @return true if supported, false otherwise
     */
    boolean supportsReturning();

    /**
     * Formats a RETURNING clause for the given columns.
     *
     * @param columns the columns to return, or empty for all columns (*)
     * @return the formatted RETURNING clause snippet
     */
    String renderReturningClause(List<String> columns);
}

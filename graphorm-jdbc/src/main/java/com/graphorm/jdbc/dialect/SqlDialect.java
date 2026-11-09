package com.graphorm.jdbc.dialect;

import com.graphorm.core.metadata.CheckConstraintMetadata;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.IndexMetadata;
import com.graphorm.core.query.BatchInsertQuery;
import com.graphorm.core.query.BulkUpdateQuery;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.core.query.UpsertQuery;
import com.graphorm.core.query.SqlQuery;
import java.util.Objects;
import com.graphorm.jdbc.ddl.ColumnDefinition;
import com.graphorm.jdbc.ddl.ForeignKeyDefinition;
import com.graphorm.jdbc.ddl.TableDefinition;
import com.graphorm.jdbc.executor.BoundSql;

import java.util.List;

/**
 * Abstraction isolating database-specific SQL dialect formatting, identifier quoting,
 * pagination, aggregation functions, type conversions, CRUD query building, and DDL schema generation.
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

    default BoundSql buildInsert(InsertQuery query) {
        return buildInsertSql(query);
    }

    /**
     * Builds a parameterized {@link BoundSql} for an {@link UpsertQuery}.
     *
     * @param query the upsert query AST
     * @return the parameterized BoundSql
     */
    BoundSql buildUpsertSql(UpsertQuery query);

    default BoundSql buildUpsert(UpsertQuery query) {
        return buildUpsertSql(query);
    }

    /**
     * Builds a parameterized {@link BoundSql} for a {@link BatchInsertQuery}.
     *
     * @param query the batch insert query AST
     * @return the parameterized BoundSql
     */
    BoundSql buildBatchInsertSql(BatchInsertQuery query);

    default BoundSql buildBatchInsert(BatchInsertQuery query) {
        return buildBatchInsertSql(query);
    }

    /**
     * Builds a parameterized {@link BoundSql} for an {@link UpdateQuery}.
     *
     * @param query the update query AST
     * @return the parameterized BoundSql
     */
    BoundSql buildUpdateSql(UpdateQuery query);

    default BoundSql buildUpdate(UpdateQuery query) {
        return buildUpdateSql(query);
    }

    /**
     * Builds a parameterized {@link BoundSql} for a {@link BulkUpdateQuery}.
     *
     * @param query the bulk update query AST
     * @return the parameterized BoundSql
     */
    BoundSql buildBulkUpdateSql(BulkUpdateQuery query);

    default BoundSql buildBulkUpdate(BulkUpdateQuery query) {
        return buildBulkUpdateSql(query);
    }

    /**
     * Builds a parameterized {@link BoundSql} for a {@link DeleteQuery}.
     *
     * @param query the delete query AST
     * @return the parameterized BoundSql
     */
    BoundSql buildDeleteSql(DeleteQuery query);

    default BoundSql buildDelete(DeleteQuery query) {
        return buildDeleteSql(query);
    }

    /**
     * Builds a parameterized {@link BoundSql} for any supported {@link SqlQuery}.
     *
     * @param query the query AST
     * @return the parameterized BoundSql
     */
    default BoundSql buildSql(SqlQuery query) {
        Objects.requireNonNull(query, "SqlQuery must not be null");
        return switch (query.queryType()) {
            case SELECT -> buildSelectSql((SelectQuery) query);
            case INSERT -> buildInsertSql((InsertQuery) query);
            case UPDATE -> buildUpdateSql((UpdateQuery) query);
            case DELETE -> buildDeleteSql((DeleteQuery) query);
            case UPSERT -> buildUpsertSql((UpsertQuery) query);
            case BATCH_INSERT -> buildBatchInsertSql((BatchInsertQuery) query);
            case BULK_UPDATE -> buildBulkUpdateSql((BulkUpdateQuery) query);
        };
    }

    /**
     * Builds an EXPLAIN plan SQL query string for this dialect.
     *
     * @param sql the SQL string to explain
     * @return the EXPLAIN statement SQL
     */
    default String buildExplainSql(String sql) {
        return "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql;
    }

    /**
     * Formats an aggregation expression into SQL for this dialect.
     *
     * @param type       the aggregation type (COUNT, SUM, AVG, MIN, MAX)
     * @param expression the column or SQL expression to aggregate
     * @return the formatted aggregation SQL expression
     */
    String buildAggregateSql(AggregateType type, String expression);

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
     * Indicates whether this dialect supports explicit NULLS FIRST / NULLS LAST ordering.
     *
     * @return true if supported, false otherwise
     */
    default boolean supportsNullsOrdering() {
        return true;
    }

    /**
     * Formats a RETURNING clause for the given columns.
     *
     * @param columns the columns to return, or empty for all columns (*)
     * @return the formatted RETURNING clause snippet
     */
    String renderReturningClause(List<String> columns);

    /**
     * Formats a database notification statement for the specified channel and payload.
     * Default implementation formats a PostgreSQL NOTIFY statement.
     *
     * @param channel the notification channel name
     * @param payload the JSON or text payload
     * @return the SQL notification statement
     */
    default String formatNotifySql(String channel, String payload) {
        Objects.requireNonNull(channel, "channel must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        String escaped = payload.replace("'", "''");
        return "NOTIFY " + channel + ", '" + escaped + "'";
    }

    // =========================================================================
    // DDL Operations
    // =========================================================================

    /**
     * Generates a CREATE TABLE SQL statement for the specified table definition.
     */
    String buildCreateTableSql(TableDefinition table);

    /**
     * Generates a DROP TABLE SQL statement.
     */
    String buildDropTableSql(String tableName, boolean ifExists, boolean cascade);

    default String buildDropTableSql(String tableName) {
        return buildDropTableSql(tableName, true, true);
    }

    /**
     * Generates an ALTER TABLE ... ADD COLUMN SQL statement.
     */
    String buildAddColumnSql(String tableName, ColumnDefinition column);

    /**
     * Generates an ALTER TABLE ... DROP COLUMN SQL statement.
     */
    String buildDropColumnSql(String tableName, String columnName, boolean ifExists);

    default String buildDropColumnSql(String tableName, String columnName) {
        return buildDropColumnSql(tableName, columnName, true);
    }

    /**
     * Generates an ALTER TABLE ... ALTER COLUMN ... TYPE SQL statement.
     */
    String buildAlterColumnTypeSql(String tableName, String columnName, String newType);

    /**
     * Generates an ALTER TABLE ... ALTER COLUMN ... [SET NOT NULL | DROP NOT NULL] SQL statement.
     */
    String buildAlterColumnNullabilitySql(String tableName, String columnName, boolean nullable);

    /**
     * Generates an ALTER TABLE ... ALTER COLUMN ... SET DEFAULT SQL statement.
     */
    String buildAlterColumnDefaultSql(String tableName, String columnName, String defaultValue);

    /**
     * Generates an ALTER TABLE ... ALTER COLUMN ... DROP DEFAULT SQL statement.
     */
    String buildDropColumnDefaultSql(String tableName, String columnName);

    /**
     * Generates a CREATE INDEX SQL statement.
     */
    String buildCreateIndexSql(IndexMetadata index, String tableName);

    /**
     * Generates a DROP INDEX SQL statement.
     */
    String buildDropIndexSql(String indexName, boolean ifExists);

    default String buildDropIndexSql(String indexName) {
        return buildDropIndexSql(indexName, true);
    }

    /**
     * Generates an ALTER TABLE ... ADD CONSTRAINT ... FOREIGN KEY statement.
     */
    String buildAddForeignKeySql(ForeignKeyDefinition fk);

    /**
     * Generates an ALTER TABLE ... DROP CONSTRAINT statement.
     */
    String buildDropConstraintSql(String tableName, String constraintName, boolean ifExists);

    default String buildDropConstraintSql(String tableName, String constraintName) {
        return buildDropConstraintSql(tableName, constraintName, true);
    }

    /**
     * Generates an ALTER TABLE ... ADD CONSTRAINT ... UNIQUE statement.
     */
    String buildAddUniqueConstraintSql(String tableName, String constraintName, List<String> columns);

    /**
     * Generates an ALTER TABLE ... ADD CONSTRAINT ... CHECK statement.
     */
    String buildAddCheckConstraintSql(String tableName, CheckConstraintMetadata check);

    /**
     * Generates a CREATE TYPE ... AS ENUM statement.
     */
    String buildCreateEnumSql(String enumName, List<String> values);

    /**
     * Generates an ALTER TYPE ... ADD VALUE statement.
     */
    String buildAddEnumValueSql(String enumName, String value);

    /**
     * Generates a DROP TYPE statement.
     */
    String buildDropEnumSql(String enumName, boolean ifExists);

    default String buildDropEnumSql(String enumName) {
        return buildDropEnumSql(enumName, true);
    }

    /**
     * Builds the SQL for acquiring an advisory lock.
     */
    String buildAdvisoryLockSql(long lockKey);

    /**
     * Builds the SQL for releasing an advisory lock.
     */
    String buildAdvisoryUnlockSql(long lockKey);

    /**
     * Returns the error translator for mapping SQLExceptions to typed GraphORM DataAccessExceptions.
     */
    default SqlErrorTranslator errorTranslator() {
        return new PostgreSqlErrorTranslator();
    }

    /**
     * Auto-detects the appropriate {@link SqlDialect} based on the JDBC database product name.
     *
     * @param databaseProductName database product name from DatabaseMetaData
     * @return matching SqlDialect (PostgreSQL, MySQL, SQLite, or PostgreSqlDialect as fallback)
     */
    static SqlDialect fromDatabaseProductName(String databaseProductName) {
        if (databaseProductName == null || databaseProductName.isBlank()) {
            return new PostgreSqlDialect();
        }
        String lower = databaseProductName.toLowerCase();
        if (lower.contains("mysql") || lower.contains("mariadb")) {
            return new MySqlDialect();
        }
        if (lower.contains("sqlite")) {
            return new SqliteDialect();
        }
        if (lower.contains("postgres")) {
            return new PostgreSqlDialect();
        }
        return new PostgreSqlDialect();
    }
}

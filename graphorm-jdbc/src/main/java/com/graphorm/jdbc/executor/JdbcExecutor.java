package com.graphorm.jdbc.executor;

import com.graphorm.core.execution.QueryExecutionEngine;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.core.query.BatchInsertQuery;
import com.graphorm.core.query.BulkUpdateQuery;
import com.graphorm.core.query.SqlQuery;
import com.graphorm.core.query.UpsertQuery;
import com.graphorm.jdbc.builder.BatchInsertBuilder;
import com.graphorm.jdbc.builder.UpsertBuilder;
import com.graphorm.jdbc.builder.InsertBuilder;
import com.graphorm.jdbc.builder.UpdateBuilder;
import com.graphorm.jdbc.cursor.Cursor;
import com.graphorm.jdbc.cursor.DefaultCursor;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.PostgreSqlErrorTranslator;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.dialect.SqlErrorTranslator;
import com.graphorm.jdbc.exception.QueryExecutionException;
import com.graphorm.jdbc.mapper.DynamicRowMapper;
import com.graphorm.jdbc.mapper.RowMapper;
import com.graphorm.jdbc.util.StatementInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * Core JDBC query execution engine managing database connections, prepared statement binding,
 * result set iteration, and resource cleanup with zero connection leaks.
 * Implements the decoupled {@link QueryExecutionEngine} SPI.
 */
public class JdbcExecutor implements QueryExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(JdbcExecutor.class);

    public static final String LOG_PARAMETERS_PROPERTY = "graphorm.jdbc.log-parameters";
    public static final int DEFAULT_FETCH_SIZE = 100;
    public static final int MAX_BATCH_INSERT_ROWS = 1000;
    public static final int MAX_BATCH_INSERT_PARAMS = 5000;

    private static volatile boolean globalLogParameters = Boolean.getBoolean(LOG_PARAMETERS_PROPERTY);

    private volatile boolean logParameters = globalLogParameters;

    /**
     * Strategy interface for acquiring and releasing database connections.
     */
    @FunctionalInterface
    public interface ConnectionProvider {
        /**
         * Acquires a database connection for query execution.
         *
         * @param dataSource the underlying data source
         * @return an open Connection
         * @throws SQLException if connection acquisition fails
         */
        Connection getConnection(DataSource dataSource) throws SQLException;

        /**
         * Releases or closes the acquired connection.
         *
         * @param connection the connection to release
         * @param dataSource the underlying data source
         * @throws SQLException if releasing the connection fails
         */
        default void releaseConnection(Connection connection, DataSource dataSource) throws SQLException {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private static final ConnectionProvider DEFAULT_CONNECTION_PROVIDER = DataSource::getConnection;

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final ConnectionProvider connectionProvider;
    private final SqlErrorTranslator errorTranslator;
    private final List<StatementInterceptor> interceptors = new CopyOnWriteArrayList<>();

    public JdbcExecutor(DataSource dataSource, SqlDialect dialect, ConnectionProvider connectionProvider, StatementInterceptor... interceptors) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource must not be null");
        this.dialect = Objects.requireNonNull(dialect, "SqlDialect must not be null");
        this.connectionProvider = connectionProvider != null ? connectionProvider : DEFAULT_CONNECTION_PROVIDER;
        this.errorTranslator = dialect.errorTranslator() != null ? dialect.errorTranslator() : new PostgreSqlErrorTranslator();
        if (interceptors != null) {
            for (StatementInterceptor i : interceptors) {
                if (i != null) {
                    this.interceptors.add(i);
                }
            }
        }
    }

    public JdbcExecutor(DataSource dataSource, SqlDialect dialect, StatementInterceptor... interceptors) {
        this(dataSource, dialect, DEFAULT_CONNECTION_PROVIDER, interceptors);
    }

    public JdbcExecutor(DataSource dataSource, SqlDialect dialect) {
        this(dataSource, dialect, DEFAULT_CONNECTION_PROVIDER);
    }

    public JdbcExecutor(DataSource dataSource) {
        this(dataSource, detectDialect(dataSource), DEFAULT_CONNECTION_PROVIDER);
    }

    /**
     * Auto-detects the appropriate {@link SqlDialect} by inspecting DataSource connection metadata.
     *
     * @param dataSource the DataSource to inspect
     * @return auto-detected dialect, or PostgreSqlDialect fallback
     */
    public static SqlDialect detectDialect(DataSource dataSource) {
        if (dataSource == null) {
            return new PostgreSqlDialect();
        }
        try (Connection conn = dataSource.getConnection()) {
            return detectDialect(conn);
        } catch (SQLException e) {
            log.warn("Failed to inspect Connection metadata from DataSource for dialect auto-detection: {}. Defaulting to PostgreSqlDialect.", e.getMessage());
            return new PostgreSqlDialect();
        }
    }

    /**
     * Auto-detects the appropriate {@link SqlDialect} by inspecting Connection metadata product name.
     *
     * @param connection the active database Connection
     * @return auto-detected dialect, or PostgreSqlDialect fallback
     */
    public static SqlDialect detectDialect(Connection connection) {
        if (connection == null) {
            return new PostgreSqlDialect();
        }
        try {
            String prodName = connection.getMetaData().getDatabaseProductName();
            return SqlDialect.fromDatabaseProductName(prodName);
        } catch (SQLException e) {
            log.warn("Failed to get database product name from Connection: {}. Defaulting to PostgreSqlDialect.", e.getMessage());
            return new PostgreSqlDialect();
        }
    }

    public SqlDialect dialect() {
        return dialect;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public ConnectionProvider connectionProvider() {
        return connectionProvider;
    }

    public SqlErrorTranslator errorTranslator() {
        return errorTranslator;
    }

    public final void addInterceptor(StatementInterceptor interceptor) {
        if (interceptor != null) {
            this.interceptors.add(interceptor);
        }
    }

    public final void removeInterceptor(StatementInterceptor interceptor) {
        this.interceptors.remove(interceptor);
    }

    public List<StatementInterceptor> interceptors() {
        return Collections.unmodifiableList(interceptors);
    }

    public void setLogParameters(boolean logParameters) {
        if (logParameters) {
            log.warn("SECURITY WARNING: Full SQL parameter logging is enabled for JdbcExecutor. " +
                    "Sensitive parameter values (passwords, PII, tokens) may be exposed in log files!");
        }
        this.logParameters = logParameters;
    }

    public boolean isLogParameters() {
        return logParameters;
    }

    public static void setGlobalLogParameters(boolean enabled) {
        if (enabled) {
            log.warn("SECURITY WARNING: Global SQL parameter logging is enabled. " +
                    "Sensitive parameter values may be exposed in log files!");
        }
        globalLogParameters = enabled;
    }

    public static boolean isGlobalLogParameters() {
        return globalLogParameters;
    }

    private String formatParametersForLog(List<Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "0 params";
        }
        if (logParameters || globalLogParameters || Boolean.getBoolean(LOG_PARAMETERS_PROPERTY)) {
            return "params: " + parameters;
        }
        return parameters.size() + " bound parameter(s) [REDACTED]";
    }

    // --- Query Operations ---
    public List<Map<String, Object>> queryForList(BoundSql boundSql) {
        return queryForList(boundSql, DynamicRowMapper.mapToMap());
    }

    @Override
    public List<Map<String, Object>> queryForList(SelectQuery query) {
        return queryForList(dialect.buildSelectSql(query));
    }

    @Override
    public List<Map<String, Object>> executeSelect(SelectQuery query) {
        return queryForList(query);
    }

    public <T> List<T> queryForList(BoundSql boundSql, Class<T> recordClass) {
        return queryForList(boundSql, DynamicRowMapper.mapToRecord(recordClass));
    }

    public <T> List<T> queryForList(SelectQuery query, Class<T> recordClass) {
        return queryForList(dialect.buildSelectSql(query), recordClass);
    }

    public <T> List<T> queryForList(SelectQuery query, RowMapper<T> rowMapper) {
        return queryForList(dialect.buildSelectSql(query), rowMapper);
    }

    public <T> List<T> queryForList(BoundSql boundSql, RowMapper<T> rowMapper) {
        Objects.requireNonNull(boundSql, "BoundSql must not be null");
        Objects.requireNonNull(rowMapper, "RowMapper must not be null");

        log.debug("Executing query: {} with {}", boundSql.sql(), formatParametersForLog(boundSql.parameters()));
        long start = System.nanoTime();
        for (StatementInterceptor interceptor : interceptors) {
            try {
                interceptor.beforeExecute(boundSql.sql(), boundSql.parameters());
            } catch (Throwable t) {
                log.error("Interceptor failed in beforeExecute: {}", t.getMessage(), t);
            }
        }

        Connection conn = null;
        try {
            conn = connectionProvider.getConnection(dataSource);
            try (PreparedStatement ps = conn.prepareStatement(boundSql.sql())) {
                bindParameters(ps, boundSql.parameters());

                try (ResultSet rs = ps.executeQuery()) {
                    List<T> results = new ArrayList<>();
                    int rowNum = 0;
                    while (rs.next()) {
                        results.add(rowMapper.mapRow(rs, rowNum++));
                    }
                    return results;
                }
            }
        } catch (SQLException e) {
            throw errorTranslator.translate("Query execution", boundSql.sql(), e);
        } finally {
            try {
                if (conn != null) {
                    try {
                        connectionProvider.releaseConnection(conn, dataSource);
                    } catch (SQLException e) {
                        log.warn("Failed to release connection: {}", e.getMessage(), e);
                    }
                }
            } finally {
                long duration = System.nanoTime() - start;
                for (StatementInterceptor interceptor : interceptors) {
                    try {
                        interceptor.afterExecute(boundSql.sql(), boundSql.parameters(), duration);
                    } catch (Throwable t) {
                        log.error("Interceptor failed in afterExecute: {}", t.getMessage(), t);
                    }
                }
            }
        }
    }

    public Optional<Map<String, Object>> queryForObject(BoundSql boundSql) {
        return queryForObject(boundSql, DynamicRowMapper.mapToMap());
    }

    @Override
    public Optional<Map<String, Object>> queryForObject(SelectQuery query) {
        return queryForObject(dialect.buildSelectSql(query));
    }

    @Override
    public Optional<Map<String, Object>> executeSelectOne(SelectQuery query) {
        return queryForObject(query);
    }

    public <T> Optional<T> queryForObject(BoundSql boundSql, Class<T> recordClass) {
        return queryForObject(boundSql, DynamicRowMapper.mapToRecord(recordClass));
    }

    public <T> Optional<T> queryForObject(SelectQuery query, Class<T> recordClass) {
        return queryForObject(dialect.buildSelectSql(query), recordClass);
    }

    public <T> Optional<T> queryForObject(SelectQuery query, RowMapper<T> rowMapper) {
        return queryForObject(dialect.buildSelectSql(query), rowMapper);
    }

    public <T> Optional<T> queryForObject(BoundSql boundSql, RowMapper<T> rowMapper) {
        List<T> list = queryForList(boundSql, rowMapper);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        if (list.size() > 1) {
            throw new QueryExecutionException("Expected single row result but found " + list.size(), boundSql.sql(), null);
        }
        return Optional.ofNullable(list.getFirst());
    }

    // --- Streaming Operations ---

    /**
     * Executes a streaming query using server-side cursor and JDBC fetch size,
     * returning a closable {@link Cursor}.
     *
     * @param boundSql  the parameterized query
     * @param rowMapper mapping strategy
     * @param fetchSize JDBC fetch size
     * @param <T>       the entity or record type
     * @return an open, streaming Cursor
     */
    public <T> Cursor<T> stream(BoundSql boundSql, RowMapper<T> rowMapper, int fetchSize) {
        Objects.requireNonNull(boundSql, "BoundSql must not be null");
        Objects.requireNonNull(rowMapper, "RowMapper must not be null");

        log.debug("Opening streaming cursor for: {} with fetchSize={}", boundSql.sql(), fetchSize);

        Connection conn = null;
        try {
            conn = connectionProvider.getConnection(dataSource);
            boolean wasAutoCommit = conn.getAutoCommit();
            if (wasAutoCommit) {
                conn.setAutoCommit(false);
            }
            PreparedStatement ps = conn.prepareStatement(boundSql.sql(), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(fetchSize > 0 ? fetchSize : DEFAULT_FETCH_SIZE);
            bindParameters(ps, boundSql.parameters());

            ResultSet rs = ps.executeQuery();
            return new DefaultCursor<>(conn, ps, rs, rowMapper, connectionProvider, dataSource, wasAutoCommit, errorTranslator, boundSql.sql());
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    connectionProvider.releaseConnection(conn, dataSource);
                } catch (SQLException ignored) {}
            }
            throw errorTranslator.translate("Streaming query execution", boundSql.sql(), e);
        }
    }

    public <T> Cursor<T> stream(BoundSql boundSql, RowMapper<T> rowMapper) {
        return stream(boundSql, rowMapper, DEFAULT_FETCH_SIZE);
    }

    public <T> Cursor<T> stream(SelectQuery query, RowMapper<T> rowMapper, int fetchSize) {
        return stream(dialect.buildSelectSql(query), rowMapper, fetchSize);
    }

    public <T> Cursor<T> stream(SelectQuery query, RowMapper<T> rowMapper) {
        return stream(query, rowMapper, DEFAULT_FETCH_SIZE);
    }

    public <T> Cursor<T> stream(SelectQuery query, Class<T> recordClass, int fetchSize) {
        return stream(dialect.buildSelectSql(query), DynamicRowMapper.mapToRecord(recordClass), fetchSize);
    }

    public <T> Cursor<T> stream(SelectQuery query, Class<T> recordClass) {
        return stream(query, recordClass, DEFAULT_FETCH_SIZE);
    }

    public <T> Cursor<T> stream(BoundSql boundSql, Class<T> recordClass, int fetchSize) {
        return stream(boundSql, DynamicRowMapper.mapToRecord(recordClass), fetchSize);
    }

    public <T> Cursor<T> stream(BoundSql boundSql, Class<T> recordClass) {
        return stream(boundSql, recordClass, DEFAULT_FETCH_SIZE);
    }

    public <T> Stream<T> queryForStream(BoundSql boundSql, RowMapper<T> rowMapper, int fetchSize) {
        return stream(boundSql, rowMapper, fetchSize).stream();
    }

    public <T> Stream<T> queryForStream(BoundSql boundSql, RowMapper<T> rowMapper) {
        return stream(boundSql, rowMapper).stream();
    }

    public <T> Stream<T> queryForStream(SelectQuery query, RowMapper<T> rowMapper, int fetchSize) {
        return stream(query, rowMapper, fetchSize).stream();
    }

    public <T> Stream<T> queryForStream(SelectQuery query, RowMapper<T> rowMapper) {
        return stream(query, rowMapper).stream();
    }

    public <T> Stream<T> queryForStream(SelectQuery query, Class<T> recordClass, int fetchSize) {
        return stream(query, recordClass, fetchSize).stream();
    }

    public <T> Stream<T> queryForStream(SelectQuery query, Class<T> recordClass) {
        return stream(query, recordClass).stream();
    }

    public <T> Stream<T> queryForStream(BoundSql boundSql, Class<T> recordClass, int fetchSize) {
        return stream(boundSql, recordClass, fetchSize).stream();
    }

    public <T> Stream<T> queryForStream(BoundSql boundSql, Class<T> recordClass) {
        return stream(boundSql, recordClass).stream();
    }

    // --- Mutation Operations ---
    public int executeUpdate(BoundSql boundSql) {
        Objects.requireNonNull(boundSql, "BoundSql must not be null");
        log.debug("Executing update: {} with {}", boundSql.sql(), formatParametersForLog(boundSql.parameters()));

        long start = System.nanoTime();
        for (StatementInterceptor interceptor : interceptors) {
            try {
                interceptor.beforeExecute(boundSql.sql(), boundSql.parameters());
            } catch (Throwable t) {
                log.error("Interceptor failed in beforeExecute: {}", t.getMessage(), t);
            }
        }

        Connection conn = null;
        try {
            conn = connectionProvider.getConnection(dataSource);
            try (PreparedStatement ps = conn.prepareStatement(boundSql.sql())) {
                bindParameters(ps, boundSql.parameters());
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw errorTranslator.translate("Update execution", boundSql.sql(), e);
        } finally {
            try {
                if (conn != null) {
                    try {
                        connectionProvider.releaseConnection(conn, dataSource);
                    } catch (SQLException e) {
                        log.warn("Failed to release connection: {}", e.getMessage(), e);
                    }
                }
            } finally {
                long duration = System.nanoTime() - start;
                for (StatementInterceptor interceptor : interceptors) {
                    try {
                        interceptor.afterExecute(boundSql.sql(), boundSql.parameters(), duration);
                    } catch (Throwable t) {
                        log.error("Interceptor failed in afterExecute: {}", t.getMessage(), t);
                    }
                }
            }
        }
    }

    public int executeUpdate(InsertQuery query) {
        return executeUpdate(dialect.buildInsertSql(query));
    }

    @Override
    public int executeInsert(InsertQuery query) {
        return executeUpdate(query);
    }

    @Override
    public int executeUpdate(UpdateQuery query) {
        return executeUpdate(dialect.buildUpdateSql(query));
    }

    @Override
    public Optional<Map<String, Object>> executeUpdateAndReturn(UpdateQuery query) {
        Objects.requireNonNull(query, "UpdateQuery must not be null");
        if (dialect.supportsReturning()) {
            BoundSql boundSql = UpdateBuilder.of(dialect)
                    .table(query.table())
                    .values(query.values())
                    .where(query.where())
                    .returningAll()
                    .build();
            return queryForObject(boundSql);
        }
        BoundSql updateBoundSql = dialect.buildUpdateSql(query);
        int affected = executeUpdate(updateBoundSql);
        if (affected == 0) {
            return Optional.empty();
        }
        if (query.where() != null) {
            SelectQuery selectQuery = SelectQuery.from(query.table().tableName())
                    .column("*")
                    .where(query.where())
                    .limit(1)
                    .build();
            return queryForObject(selectQuery);
        }
        return Optional.of(new LinkedHashMap<>(query.values()));
    }

    /**
     * Executes an update configured via {@link UpdateBuilder}, automatically verifying
     * optimistic locking concurrency control if enabled.
     */
    public int executeUpdate(UpdateBuilder updateBuilder) {
        Objects.requireNonNull(updateBuilder, "UpdateBuilder must not be null");
        BoundSql boundSql = updateBuilder.build();
        int affected = executeUpdate(boundSql);
        updateBuilder.verifyOptimisticLock(affected);
        return affected;
    }

    @Override
    public int executeUpdate(DeleteQuery query) {
        return executeUpdate(dialect.buildDeleteSql(query));
    }

    @Override
    public int executeDelete(DeleteQuery query) {
        return executeUpdate(query);
    }

    public int executeUpsert(UpsertQuery query) {
        return executeUpdate(dialect.buildUpsertSql(query));
    }

    public Optional<Map<String, Object>> executeUpsertAndReturn(UpsertQuery query) {
        if (query.returning() != null && !query.returning().isEmpty()) {
            return queryForObject(dialect.buildUpsertSql(query));
        }
        if (dialect.supportsReturning()) {
            UpsertQuery returningQuery = query.toBuilder().returningAll().build();
            return queryForObject(dialect.buildUpsertSql(returningQuery));
        }
        int rows = executeUpdate(dialect.buildUpsertSql(query));
        return rows > 0 ? Optional.of(new LinkedHashMap<>(query.values())) : Optional.empty();
    }

    public <T> Optional<T> executeUpsertAndReturn(UpsertQuery query, Class<T> targetClass) {
        return executeUpsertAndReturn(query).map(map -> DynamicRowMapper.mapMapToRecord(map, targetClass));
    }

    /**
     * Executes a batch INSERT query, transparently partitioning large row sets into
     * chunks to safeguard against bind parameter limits.
     */
    @Override
    public int executeBatchInsert(BatchInsertQuery query) {
        Objects.requireNonNull(query, "BatchInsertQuery must not be null");
        int rowCount = query.rows().size();
        int colCount = query.columns().size();
        int totalParams = rowCount * colCount;

        if (rowCount > MAX_BATCH_INSERT_ROWS || totalParams > MAX_BATCH_INSERT_PARAMS) {
            int totalInserted = 0;
            int chunkSize = Math.max(1, Math.min(MAX_BATCH_INSERT_ROWS, MAX_BATCH_INSERT_PARAMS / Math.max(1, colCount)));
            for (int i = 0; i < rowCount; i += chunkSize) {
                int end = Math.min(i + chunkSize, rowCount);
                List<List<Object>> chunkRows = query.rows().subList(i, end);
                BatchInsertQuery chunkQuery = BatchInsertQuery.into(query.table())
                        .columns(query.columns())
                        .rows(chunkRows)
                        .returning(query.returningColumns())
                        .build();
                totalInserted += executeUpdate(dialect.buildBatchInsertSql(chunkQuery));
            }
            return totalInserted;
        }

        return executeUpdate(dialect.buildBatchInsertSql(query));
    }

    /**
     * Executes a batch INSERT query with RETURNING clause, chunking large batches transparently.
     */
    @Override
    public List<Map<String, Object>> executeBatchInsertAndReturn(BatchInsertQuery query) {
        Objects.requireNonNull(query, "BatchInsertQuery must not be null");
        int rowCount = query.rows().size();
        int colCount = query.columns().size();
        int totalParams = rowCount * colCount;

        if (rowCount > MAX_BATCH_INSERT_ROWS || totalParams > MAX_BATCH_INSERT_PARAMS) {
            List<Map<String, Object>> allResults = new ArrayList<>();
            int chunkSize = Math.max(1, Math.min(MAX_BATCH_INSERT_ROWS, MAX_BATCH_INSERT_PARAMS / Math.max(1, colCount)));
            for (int i = 0; i < rowCount; i += chunkSize) {
                int end = Math.min(i + chunkSize, rowCount);
                List<List<Object>> chunkRows = query.rows().subList(i, end);
                BatchInsertQuery chunkQuery = BatchInsertQuery.into(query.table())
                        .columns(query.columns())
                        .rows(chunkRows)
                        .returning(query.returningColumns())
                        .build();
                allResults.addAll(executeBatchInsertAndReturn(chunkQuery));
            }
            return Collections.unmodifiableList(allResults);
        }

        if (query.returning() != null && !query.returning().isEmpty()) {
            return queryForList(dialect.buildBatchInsertSql(query));
        }
        if (dialect.supportsReturning()) {
            BatchInsertQuery returningQuery = query.toBuilder().returningAll().build();
            return queryForList(dialect.buildBatchInsertSql(returningQuery));
        }
        executeUpdate(dialect.buildBatchInsertSql(query));
        return Collections.emptyList();
    }

    public <T> List<T> executeBatchInsertAndReturn(BatchInsertQuery query, Class<T> targetClass) {
        return executeBatchInsertAndReturn(query).stream()
                .map(map -> DynamicRowMapper.mapMapToRecord(map, targetClass))
                .toList();
    }

    public int executeBulkUpdate(BulkUpdateQuery query) {
        return executeUpdate(dialect.buildBulkUpdateSql(query));
    }

    public int[] executeBatch(String sql, List<List<Object>> parameterBatches) {
        Objects.requireNonNull(sql, "SQL must not be null");
        if (parameterBatches == null || parameterBatches.isEmpty()) {
            return new int[0];
        }

        log.debug("Executing batch ({} statements): {}", parameterBatches.size(), sql);
        long start = System.nanoTime();
        for (StatementInterceptor interceptor : interceptors) {
            try {
                interceptor.beforeExecute(sql, parameterBatches.isEmpty() ? List.of() : parameterBatches.get(0));
            } catch (Throwable t) {
                log.error("Interceptor failed in beforeExecute: {}", t.getMessage(), t);
            }
        }

        Connection conn = null;
        try {
            conn = connectionProvider.getConnection(dataSource);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (List<Object> params : parameterBatches) {
                    bindParameters(ps, params);
                    ps.addBatch();
                }
                return ps.executeBatch();
            }
        } catch (SQLException e) {
            throw errorTranslator.translate("Batch execution", sql, e);
        } finally {
            try {
                if (conn != null) {
                    try {
                        connectionProvider.releaseConnection(conn, dataSource);
                    } catch (SQLException e) {
                        log.warn("Failed to release connection: {}", e.getMessage(), e);
                    }
                }
            } finally {
                long duration = System.nanoTime() - start;
                for (StatementInterceptor interceptor : interceptors) {
                    try {
                        interceptor.afterExecute(sql, parameterBatches.isEmpty() ? List.of() : parameterBatches.get(0), duration);
                    } catch (Throwable t) {
                        log.error("Interceptor failed in afterExecute: {}", t.getMessage(), t);
                    }
                }
            }
        }
    }

    public int[] executeBatch(List<BoundSql> queries) {
        if (queries == null || queries.isEmpty()) {
            return new int[0];
        }
        String firstSql = queries.get(0).sql();
        boolean sameSql = queries.stream().allMatch(q -> Objects.equals(q.sql(), firstSql));
        if (sameSql) {
            List<List<Object>> batches = queries.stream().map(BoundSql::parameters).toList();
            return executeBatch(firstSql, batches);
        }
        int[] counts = new int[queries.size()];
        for (int i = 0; i < queries.size(); i++) {
            counts[i] = executeUpdate(queries.get(i));
        }
        return counts;
    }

    public String explainPlan(SqlQuery query) {
        Objects.requireNonNull(query, "SqlQuery must not be null");
        return explainPlan(dialect.buildSql(query));
    }

    public String explainPlan(BoundSql boundSql) {
        Objects.requireNonNull(boundSql, "BoundSql must not be null");
        String explainSql = dialect.buildExplainSql(boundSql.sql());
        BoundSql explainBoundSql = new BoundSql(explainSql, boundSql.parameters());
        List<Map<String, Object>> rows = queryForList(explainBoundSql);
        if (rows.isEmpty()) {
            return "";
        }
        Object val = rows.get(0).values().iterator().next();
        return val != null ? val.toString() : "";
    }

    /**
     * Executes an INSERT query with a RETURNING clause and maps the returned row.
     * If the dialect does not support RETURNING, falls back to Statement.RETURN_GENERATED_KEYS.
     *
     * @param query       the insert query AST
     * @param targetClass the return record or model class
     * @param <T>         the return type
     * @return the returned instance if mapped
     */
    public <T> Optional<T> executeInsertAndReturn(InsertQuery query, Class<T> targetClass) {
        if (dialect.supportsReturning()) {
            BoundSql boundSql = InsertBuilder.of(dialect)
                    .into(query.table())
                    .values(query.values())
                    .returningAll()
                    .build();
            return queryForObject(boundSql, targetClass);
        }
        return executeInsertWithGeneratedKeys(query, targetClass);
    }

    /**
     * Executes an INSERT query with a RETURNING clause and maps the returned row to a Map.
     * If the dialect does not support RETURNING, falls back to Statement.RETURN_GENERATED_KEYS.
     *
     * @param query the insert query AST
     * @return map of returned column values
     */
    @Override
    public Optional<Map<String, Object>> executeInsertAndReturn(InsertQuery query) {
        if (dialect.supportsReturning()) {
            BoundSql boundSql = InsertBuilder.of(dialect)
                    .into(query.table())
                    .values(query.values())
                    .returningAll()
                    .build();
            return queryForObject(boundSql);
        }
        return executeInsertWithGeneratedKeys(query);
    }

    private Optional<Map<String, Object>> executeInsertWithGeneratedKeys(InsertQuery query) {
        BoundSql boundSql = InsertBuilder.of(dialect).build(query);
        log.debug("Executing insert with generated keys: {} with {}", boundSql.sql(), formatParametersForLog(boundSql.parameters()));

        long start = System.nanoTime();
        for (StatementInterceptor interceptor : interceptors) {
            try {
                interceptor.beforeExecute(boundSql.sql(), boundSql.parameters());
            } catch (Throwable t) {
                log.error("Interceptor failed in beforeExecute: {}", t.getMessage(), t);
            }
        }

        Connection conn = null;
        try {
            conn = connectionProvider.getConnection(dataSource);
            try (PreparedStatement ps = conn.prepareStatement(boundSql.sql(), Statement.RETURN_GENERATED_KEYS)) {
                bindParameters(ps, boundSql.parameters());
                int affectedRows = ps.executeUpdate();
                if (affectedRows == 0) {
                    return Optional.empty();
                }

                Map<String, Object> result = new LinkedHashMap<>(query.values());
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs != null && rs.next()) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int columnCount = meta.getColumnCount();
                        for (int i = 1; i <= columnCount; i++) {
                            String label = meta.getColumnLabel(i);
                            if (label == null || label.isBlank()) {
                                label = meta.getColumnName(i);
                            }
                            Object val = rs.getObject(i);
                            result.put(label, val);
                            if (columnCount == 1 && !result.containsKey("id")) {
                                result.put("id", val);
                            }
                        }
                    }
                }
                return Optional.of(result);
            }
        } catch (SQLException e) {
            throw errorTranslator.translate("Insert execution", boundSql.sql(), e);
        } finally {
            try {
                if (conn != null) {
                    try {
                        connectionProvider.releaseConnection(conn, dataSource);
                    } catch (SQLException e) {
                        log.warn("Failed to release connection: {}", e.getMessage(), e);
                    }
                }
            } finally {
                long duration = System.nanoTime() - start;
                for (StatementInterceptor interceptor : interceptors) {
                    try {
                        interceptor.afterExecute(boundSql.sql(), boundSql.parameters(), duration);
                    } catch (Throwable t) {
                        log.error("Interceptor failed in afterExecute: {}", t.getMessage(), t);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> executeInsertWithGeneratedKeys(InsertQuery query, Class<T> targetClass) {
        Optional<Map<String, Object>> mapOpt = executeInsertWithGeneratedKeys(query);
        if (mapOpt.isEmpty()) {
            return Optional.empty();
        }
        if (Map.class.isAssignableFrom(targetClass)) {
            return (Optional<T>) mapOpt;
        }
        return mapOpt.map(map -> DynamicRowMapper.mapMapToRecord(map, targetClass));
    }

    // --- Parameter Binding ---
    private void bindParameters(PreparedStatement ps, List<Object> parameters) throws SQLException {
        if (parameters == null) {
            return;
        }
        for (int i = 0; i < parameters.size(); i++) {
            int parameterIndex = i + 1;
            Object param = parameters.get(i);
            bindParameter(ps, parameterIndex, param);
        }
    }

    private void bindParameter(PreparedStatement ps, int index, Object param) throws SQLException {
        if (param == null) {
            ps.setNull(index, Types.NULL);
            return;
        }

        if (param instanceof LocalDate ld) {
            ps.setDate(index, Date.valueOf(ld));
        } else if (param instanceof LocalTime lt) {
            ps.setTime(index, Time.valueOf(lt));
        } else if (param instanceof LocalDateTime ldt) {
            ps.setTimestamp(index, Timestamp.valueOf(ldt));
        } else if (param instanceof Instant inst) {
            ps.setTimestamp(index, Timestamp.from(inst));
        } else if (param instanceof Enum<?> e) {
            ps.setString(index, e.name());
        } else {
            ps.setObject(index, param);
        }
    }
}

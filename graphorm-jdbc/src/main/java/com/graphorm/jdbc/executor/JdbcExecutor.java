package com.graphorm.jdbc.executor;

import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.jdbc.builder.InsertBuilder;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
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

/**
 * Core JDBC query execution engine managing database connections, prepared statement binding,
 * result set iteration, and resource cleanup with zero connection leaks.
 */
public class JdbcExecutor {

    private static final Logger log = LoggerFactory.getLogger(JdbcExecutor.class);

    public static final String LOG_PARAMETERS_PROPERTY = "graphorm.jdbc.log-parameters";
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
    private final List<StatementInterceptor> interceptors = new CopyOnWriteArrayList<>();

    public JdbcExecutor(DataSource dataSource, SqlDialect dialect, ConnectionProvider connectionProvider, StatementInterceptor... interceptors) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource must not be null");
        this.dialect = Objects.requireNonNull(dialect, "SqlDialect must not be null");
        this.connectionProvider = connectionProvider != null ? connectionProvider : DEFAULT_CONNECTION_PROVIDER;
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
        this(dataSource, new PostgreSqlDialect(), DEFAULT_CONNECTION_PROVIDER);
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

    public List<Map<String, Object>> queryForList(SelectQuery query) {
        return queryForList(dialect.buildSelectSql(query));
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
            throw new QueryExecutionException("Query execution failed: " + e.getMessage(), boundSql.sql(), e);
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

    public Optional<Map<String, Object>> queryForObject(SelectQuery query) {
        return queryForObject(dialect.buildSelectSql(query));
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
            throw new QueryExecutionException("Update execution failed: " + e.getMessage(), boundSql.sql(), e);
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

    public int executeUpdate(UpdateQuery query) {
        return executeUpdate(dialect.buildUpdateSql(query));
    }

    public int executeUpdate(DeleteQuery query) {
        return executeUpdate(dialect.buildDeleteSql(query));
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
            throw new QueryExecutionException("Insert execution failed: " + e.getMessage(), boundSql.sql(), e);
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

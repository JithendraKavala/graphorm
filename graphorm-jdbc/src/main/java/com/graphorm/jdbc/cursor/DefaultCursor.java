package com.graphorm.jdbc.cursor;

import com.graphorm.jdbc.dialect.SqlErrorTranslator;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.mapper.RowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default implementation of {@link Cursor} wrapping open JDBC resources.
 */
public final class DefaultCursor<T> implements Cursor<T> {

    private static final Logger log = LoggerFactory.getLogger(DefaultCursor.class);

    private final Connection connection;
    private final PreparedStatement statement;
    private final ResultSet resultSet;
    private final RowMapper<T> rowMapper;
    private final JdbcExecutor.ConnectionProvider connectionProvider;
    private final DataSource dataSource;
    private final boolean wasAutoCommit;
    private final SqlErrorTranslator errorTranslator;
    private final String sql;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private boolean hasRead = false;
    private boolean hasNext = false;
    private int rowNum = 0;

    public DefaultCursor(
            Connection connection,
            PreparedStatement statement,
            ResultSet resultSet,
            RowMapper<T> rowMapper
    ) {
        this(connection, statement, resultSet, rowMapper, null, null, false, null, "SELECT");
    }

    public DefaultCursor(
            Connection connection,
            PreparedStatement statement,
            ResultSet resultSet,
            RowMapper<T> rowMapper,
            JdbcExecutor.ConnectionProvider connectionProvider,
            DataSource dataSource,
            boolean wasAutoCommit,
            SqlErrorTranslator errorTranslator,
            String sql
    ) {
        this.connection = Objects.requireNonNull(connection, "Connection must not be null");
        this.statement = Objects.requireNonNull(statement, "PreparedStatement must not be null");
        this.resultSet = Objects.requireNonNull(resultSet, "ResultSet must not be null");
        this.rowMapper = Objects.requireNonNull(rowMapper, "RowMapper must not be null");
        this.connectionProvider = connectionProvider;
        this.dataSource = dataSource;
        this.wasAutoCommit = wasAutoCommit;
        this.errorTranslator = errorTranslator;
        this.sql = sql;
    }

    @Override
    public boolean hasNext() {
        if (closed.get()) {
            return false;
        }
        if (!hasRead) {
            advance();
        }
        return hasNext;
    }

    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more elements in database cursor");
        }
        hasRead = false;
        try {
            return rowMapper.mapRow(resultSet, rowNum++);
        } catch (SQLException e) {
            closeQuietly();
            if (errorTranslator != null) {
                throw errorTranslator.translate("cursor map row", sql, e);
            }
            throw new RuntimeException("Error reading from cursor at row " + rowNum, e);
        }
    }

    private void advance() {
        try {
            hasNext = resultSet.next();
            hasRead = true;
            if (!hasNext) {
                close();
            }
        } catch (SQLException e) {
            closeQuietly();
            if (errorTranslator != null) {
                throw errorTranslator.translate("cursor advance", sql, e);
            }
            throw new RuntimeException("Error advancing database cursor", e);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closeQuietly();
        }
    }

    private void closeQuietly() {
        try {
            if (resultSet != null && !resultSet.isClosed()) {
                resultSet.close();
            }
        } catch (Exception e) {
            log.trace("Error closing cursor result set: {}", e.getMessage());
        }

        try {
            if (statement != null && !statement.isClosed()) {
                statement.close();
            }
        } catch (Exception e) {
            log.trace("Error closing cursor statement: {}", e.getMessage());
        }

        if (connection != null) {
            try {
                if (wasAutoCommit && !connection.isClosed()) {
                    try {
                        connection.commit();
                    } catch (Exception ignored) {}
                    try {
                        connection.setAutoCommit(true);
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                log.trace("Error restoring autocommit on cursor close: {}", e.getMessage());
            }

            try {
                if (connectionProvider != null) {
                    connectionProvider.releaseConnection(connection, dataSource);
                } else if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (Exception e) {
                log.trace("Error closing cursor connection: {}", e.getMessage());
            }
        }
    }
}

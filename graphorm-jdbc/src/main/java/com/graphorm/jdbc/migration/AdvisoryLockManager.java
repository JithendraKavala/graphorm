package com.graphorm.jdbc.migration;

import com.graphorm.jdbc.dialect.SqlDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Manages PostgreSQL advisory locks to guarantee that concurrent migration runners
 * do not execute simultaneously or corrupt the migration ledger.
 */
public final class AdvisoryLockManager {

    private static final Logger log = LoggerFactory.getLogger(AdvisoryLockManager.class);
    public static final long DEFAULT_LOCK_KEY = 424242424L;

    private final SqlDialect dialect;
    private final long lockKey;

    public AdvisoryLockManager(SqlDialect dialect) {
        this(dialect, DEFAULT_LOCK_KEY);
    }

    public AdvisoryLockManager(SqlDialect dialect, long lockKey) {
        this.dialect = Objects.requireNonNull(dialect, "SqlDialect must not be null");
        this.lockKey = lockKey;
    }

    /**
     * Blocks until the session-level advisory lock is acquired.
     *
     * @param conn the JDBC connection
     * @throws SQLException if a database error occurs
     */
    public void acquireLock(Connection conn) throws SQLException {
        log.debug("Acquiring migration advisory lock ({})", lockKey);
        String sql = dialect.buildAdvisoryLockSql(lockKey);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        }
        log.debug("Acquired migration advisory lock ({})", lockKey);
    }

    /**
     * Releases the session-level advisory lock.
     *
     * @param conn the JDBC connection
     * @throws SQLException if a database error occurs
     */
    public void releaseLock(Connection conn) throws SQLException {
        log.debug("Releasing migration advisory lock ({})", lockKey);
        String sql = dialect.buildAdvisoryUnlockSql(lockKey);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        }
        log.debug("Released migration advisory lock ({})", lockKey);
    }

    /**
     * Executes the given callback inside an acquired advisory lock, guaranteeing release on exit.
     */
    public <T> T withLock(Connection conn, SqlCallable<T> callable) throws SQLException, IOException {
        acquireLock(conn);
        try {
            return callable.call();
        } finally {
            try {
                releaseLock(conn);
            } catch (SQLException e) {
                log.error("Failed to release migration advisory lock ({})", lockKey, e);
            }
        }
    }

    @FunctionalInterface
    public interface SqlCallable<T> {
        T call() throws SQLException, IOException;
    }
}

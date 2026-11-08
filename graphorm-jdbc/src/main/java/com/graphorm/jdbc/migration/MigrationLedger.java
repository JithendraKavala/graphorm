package com.graphorm.jdbc.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Manages the {@code _graphorm_migrations} database tracking ledger table.
 */
public final class MigrationLedger {

    private static final Logger log = LoggerFactory.getLogger(MigrationLedger.class);
    public static final String LEDGER_TABLE_NAME = "_graphorm_migrations";

    /**
     * Creates the ledger table if it does not already exist.
     *
     * @param conn the JDBC connection
     * @throws SQLException if table creation fails
     */
    public void ensureLedgerTableExists(Connection conn) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS _graphorm_migrations (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL UNIQUE,
                    checksum VARCHAR(64) NOT NULL,
                    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    execution_time_ms BIGINT NOT NULL
                );
                """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * Records a newly executed migration in the ledger table.
     *
     * @param conn            the JDBC connection
     * @param name            the migration name
     * @param checksum        the computed SHA-256 checksum
     * @param executionTimeMs execution duration in ms
     * @throws SQLException if insert fails
     */
    public void recordMigration(Connection conn, String name, String checksum, long executionTimeMs) throws SQLException {
        Objects.requireNonNull(name, "Migration name must not be null");
        Objects.requireNonNull(checksum, "Checksum must not be null");

        String sql = "INSERT INTO _graphorm_migrations (name, checksum, execution_time_ms) VALUES (?, ?, ?);";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, checksum);
            ps.setLong(3, executionTimeMs);
            ps.executeUpdate();
        }
    }

    /**
     * Retrieves all recorded migrations in execution order.
     *
     * @param conn the JDBC connection
     * @return ordered list of applied migrations
     * @throws SQLException if query fails
     */
    public List<AppliedMigration> getAppliedMigrations(Connection conn) throws SQLException {
        ensureLedgerTableExists(conn);

        List<AppliedMigration> list = new ArrayList<>();
        String sql = "SELECT id, name, checksum, applied_at, execution_time_ms FROM _graphorm_migrations ORDER BY id ASC;";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String checksum = rs.getString("checksum");
                Timestamp ts = rs.getTimestamp("applied_at");
                Instant appliedAt = ts != null ? ts.toInstant() : Instant.now();
                long execTime = rs.getLong("execution_time_ms");

                list.add(new AppliedMigration(id, name, checksum, appliedAt, execTime));
            }
        }
        return List.copyOf(list);
    }

    /**
     * Looks up an applied migration by its name.
     *
     * @param conn the JDBC connection
     * @param name the migration name
     * @return optional containing the applied migration if found
     * @throws SQLException if query fails
     */
    public Optional<AppliedMigration> findMigration(Connection conn, String name) throws SQLException {
        ensureLedgerTableExists(conn);

        String sql = "SELECT id, name, checksum, applied_at, execution_time_ms FROM _graphorm_migrations WHERE name = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String checksum = rs.getString("checksum");
                    Timestamp ts = rs.getTimestamp("applied_at");
                    Instant appliedAt = ts != null ? ts.toInstant() : Instant.now();
                    long execTime = rs.getLong("execution_time_ms");

                    return Optional.of(new AppliedMigration(id, name, checksum, appliedAt, execTime));
                }
            }
        }
        return Optional.empty();
    }
}

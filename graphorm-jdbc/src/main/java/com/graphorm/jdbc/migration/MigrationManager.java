package com.graphorm.jdbc.migration;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.jdbc.ddl.DatabaseSchema;
import com.graphorm.jdbc.ddl.diff.MigrationPlan;
import com.graphorm.jdbc.ddl.diff.SchemaDiff;
import com.graphorm.jdbc.ddl.diff.SchemaDiffer;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * High-level orchestration manager for schema migrations, verification (drift detection),
 * status checking, and automatic migration script generation.
 */
public final class MigrationManager {

    private static final Logger log = LoggerFactory.getLogger(MigrationManager.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SqlDialect dialect;
    private final MigrationLedger ledger;
    private final AdvisoryLockManager lockManager;

    public MigrationManager() {
        this(new PostgreSqlDialect());
    }

    public MigrationManager(SqlDialect dialect) {
        this.dialect = Objects.requireNonNull(dialect, "SqlDialect must not be null");
        this.ledger = new MigrationLedger();
        this.lockManager = new AdvisoryLockManager(dialect);
    }

    public MigrationManager(SqlDialect dialect, MigrationLedger ledger, AdvisoryLockManager lockManager) {
        this.dialect = Objects.requireNonNull(dialect, "SqlDialect must not be null");
        this.ledger = Objects.requireNonNull(ledger, "MigrationLedger must not be null");
        this.lockManager = Objects.requireNonNull(lockManager, "AdvisoryLockManager must not be null");
    }

    /**
     * Inspects the migrations directory and the database ledger to report applied,
     * pending, and tampered migrations.
     *
     * @param migrationsDir root directory where migration scripts are located
     * @param conn          active JDBC connection
     * @return the migration status report
     * @throws SQLException if a database error occurs
     * @throws IOException  if reading migration files fails
     */
    public MigrationStatus status(Path migrationsDir, Connection conn) throws SQLException, IOException {
        Objects.requireNonNull(migrationsDir, "migrationsDir must not be null");
        Objects.requireNonNull(conn, "Connection must not be null");

        List<MigrationFile> diskFiles = MigrationFile.loadAll(migrationsDir);
        List<AppliedMigration> applied = ledger.getAppliedMigrations(conn);

        Map<String, AppliedMigration> appliedMap = new LinkedHashMap<>();
        for (AppliedMigration m : applied) {
            appliedMap.put(m.name(), m);
        }

        List<MigrationFile> pending = new ArrayList<>();
        List<MigrationTamper> tampered = new ArrayList<>();

        for (MigrationFile file : diskFiles) {
            AppliedMigration appliedRecord = appliedMap.get(file.name());
            if (appliedRecord == null) {
                pending.add(file);
            } else {
                if (!appliedRecord.checksum().equalsIgnoreCase(file.checksum())) {
                    tampered.add(new MigrationTamper(file.name(), appliedRecord.checksum(), file.checksum()));
                }
            }
        }

        return new MigrationStatus(applied, pending, tampered);
    }

    /**
     * Applies all pending migrations in alphabetical sequence within individual transactions
     * guarded by a session-level advisory lock.
     *
     * @param migrationsDir root directory where migration scripts are located
     * @param conn          active JDBC connection
     * @return result of applied migrations
     * @throws SQLException if execution or locking fails
     * @throws IOException  if reading migration files fails
     */
    public MigrationResult apply(Path migrationsDir, Connection conn) throws SQLException, IOException {
        Objects.requireNonNull(migrationsDir, "migrationsDir must not be null");
        Objects.requireNonNull(conn, "Connection must not be null");

        return lockManager.withLock(conn, () -> {
            MigrationStatus currentStatus = status(migrationsDir, conn);

            if (currentStatus.hasTampered()) {
                MigrationTamper t = currentStatus.tamperedMigrations().get(0);
                throw new MigrationException(
                        "Migration checksum mismatch for '" + t.migrationName() + "': applied=" + t.appliedChecksum() + ", disk=" + t.diskChecksum());
            }

            if (!currentStatus.hasPending()) {
                log.info("No pending migrations to apply.");
                return new MigrationResult(List.of(), 0, true);
            }

            List<String> appliedNames = new ArrayList<>();
            long totalStart = System.currentTimeMillis();

            boolean origAutoCommit = conn.getAutoCommit();
            try {
                for (MigrationFile file : currentStatus.pendingMigrations()) {
                    log.info("Applying migration: {}", file.name());
                    long start = System.currentTimeMillis();

                    conn.setAutoCommit(false);
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(file.content());
                    } catch (SQLException e) {
                        conn.rollback();
                        throw new MigrationException("Failed to apply migration '" + file.name() + "': " + e.getMessage(), e);
                    }

                    long duration = System.currentTimeMillis() - start;
                    ledger.recordMigration(conn, file.name(), file.checksum(), duration);
                    conn.commit();

                    appliedNames.add(file.name());
                    log.info("Successfully applied migration '{}' in {}ms", file.name(), duration);
                }
            } finally {
                conn.setAutoCommit(origAutoCommit);
            }

            long totalDuration = System.currentTimeMillis() - totalStart;
            return new MigrationResult(appliedNames, totalDuration, true);
        });
    }

    /**
     * Verifies whether the live database schema matches the expected schema declared in the ModelRegistry (drift detection).
     *
     * @param expectedRegistry target compiled ModelRegistry
     * @param conn             active JDBC connection
     * @param schemaName       database schema name (e.g. "public")
     * @return the calculated schema diff (empty if no drift)
     * @throws SQLException if catalog introspection fails
     */
    public SchemaDiff verify(ModelRegistry expectedRegistry, Connection conn, String schemaName) throws SQLException {
        Objects.requireNonNull(expectedRegistry, "ModelRegistry must not be null");
        Objects.requireNonNull(conn, "Connection must not be null");
        String schema = (schemaName != null && !schemaName.isBlank()) ? schemaName : "public";

        DatabaseSchema liveSchema = DatabaseSchema.fromCatalog(conn, schema);
        DatabaseSchema expectedSchema = DatabaseSchema.fromModelRegistry(expectedRegistry);

        return SchemaDiffer.diff(liveSchema, expectedSchema);
    }

    /**
     * Automatically diffs the baseline against the target ModelRegistry and generates a new migration file.
     *
     * @param migrationName  human-readable migration description (e.g. "add_users")
     * @param migrationsDir  directory to store migration scripts
     * @param targetRegistry desired schema state
     * @param conn           active JDBC connection (or null if generating from scratch)
     * @param schemaName     schema name (e.g. "public")
     * @return path to generated migration.sql, or empty if no changes detected
     * @throws SQLException if catalog introspection fails
     * @throws IOException  if file writing fails
     */
    public Optional<Path> generate(String migrationName, Path migrationsDir, ModelRegistry targetRegistry,
                                  Connection conn, String schemaName) throws SQLException, IOException {
        Objects.requireNonNull(migrationName, "Migration name must not be null");
        Objects.requireNonNull(migrationsDir, "migrationsDir must not be null");
        Objects.requireNonNull(targetRegistry, "targetRegistry must not be null");
        String schema = (schemaName != null && !schemaName.isBlank()) ? schemaName : "public";

        DatabaseSchema baseline;
        if (conn != null) {
            baseline = DatabaseSchema.fromCatalog(conn, schema);
        } else {
            baseline = new DatabaseSchema(schema, Collections.emptyMap(), Collections.emptyMap());
        }

        DatabaseSchema target = DatabaseSchema.fromModelRegistry(targetRegistry);
        SchemaDiff diff = SchemaDiffer.diff(baseline, target);

        if (diff.isEmpty()) {
            log.info("No schema changes detected. No migration file generated.");
            return Optional.empty();
        }

        List<String> statements = MigrationPlan.generateSql(diff, dialect);
        if (statements.isEmpty()) {
            return Optional.empty();
        }

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String cleanName = sanitizeName(migrationName);
        String folderName = timestamp + "_" + cleanName;

        Path migrationFolder = migrationsDir.resolve(folderName);
        Files.createDirectories(migrationFolder);

        Path scriptFile = migrationFolder.resolve("migration.sql");
        String content = String.join("\n\n", statements) + "\n";
        Files.writeString(scriptFile, content);

        log.info("Generated migration script at: {}", scriptFile);
        return Optional.of(scriptFile);
    }

    private String sanitizeName(String raw) {
        return raw.trim().toLowerCase().replaceAll("[^a-z0-9_]+", "_");
    }
}

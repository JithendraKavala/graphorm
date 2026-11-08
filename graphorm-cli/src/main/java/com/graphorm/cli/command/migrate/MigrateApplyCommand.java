package com.graphorm.cli.command.migrate;

import com.graphorm.jdbc.migration.MigrationManager;
import com.graphorm.jdbc.migration.MigrationResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.concurrent.Callable;

/**
 * Applies pending database migrations.
 */
@Command(
        name = "apply",
        description = "Apply pending migrations to the database",
        mixinStandardHelpOptions = true
)
public class MigrateApplyCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(
            names = {"-d", "--migrations-dir"},
            description = "Directory storing migration files (default: ${DEFAULT-VALUE})",
            defaultValue = "migrations"
    )
    private Path migrationsDir = Path.of("migrations");

    @Option(
            names = {"--url"},
            description = "Database JDBC URL"
    )
    private String jdbcUrl;

    @Option(
            names = {"-u", "--user"},
            description = "Database user (default: ${DEFAULT-VALUE})",
            defaultValue = "postgres"
    )
    private String username = "postgres";

    @Option(
            names = {"-p", "--password"},
            description = "Database password (default: ${DEFAULT-VALUE})",
            defaultValue = ""
    )
    private String password = "";

    @Option(
            names = {"--db-schema"},
            description = "Database schema name (default: ${DEFAULT-VALUE})",
            defaultValue = "public"
    )
    private String dbSchema = "public";

    public MigrateApplyCommand() {}

    public MigrateApplyCommand(Path migrationsDir, String jdbcUrl, String username, String password) {
        this(migrationsDir, jdbcUrl, username, password, "public");
    }

    public MigrateApplyCommand(Path migrationsDir, String jdbcUrl, String username, String password, String dbSchema) {
        this.migrationsDir = migrationsDir;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.dbSchema = dbSchema;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
        PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);

        MigrationManager manager = new MigrationManager();
        try (Connection conn = MigrateHelper.openConnection(jdbcUrl, username, password, dbSchema)) {
            MigrationResult result = manager.apply(migrationsDir, conn);
            if (result.appliedMigrationNames().isEmpty()) {
                out.println("No pending migrations to apply. Database is up to date.");
            } else {
                out.println("Applied " + result.appliedMigrationNames().size() + " migration(s) in " + result.totalDurationMs() + "ms:");
                for (String name : result.appliedMigrationNames()) {
                    out.println("  ✓ " + name);
                }
            }
            return 0;
        } catch (Exception e) {
            err.println("Migration failed: " + e.getMessage());
            return 1;
        }
    }
}

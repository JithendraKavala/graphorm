package com.graphorm.cli.command.migrate;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.jdbc.migration.MigrationManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Generates a new migration script by comparing schema.graphorm with the live database.
 */
@Command(
        name = "create",
        description = "Generate a new migration script by diffing schema.graphorm against the database",
        mixinStandardHelpOptions = true
)
public class MigrateCreateCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Parameters(
            index = "0",
            description = "Name/description of the migration (e.g. init, add_users)"
    )
    private String name;

    @Option(
            names = {"-s", "--schema"},
            description = "Path to the GraphORM schema file (default: ${DEFAULT-VALUE})",
            defaultValue = "schema.graphorm"
    )
    private Path schemaPath = Path.of("schema.graphorm");

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

    public MigrateCreateCommand() {}

    public MigrateCreateCommand(String name, Path schemaPath, Path migrationsDir, String jdbcUrl, String username, String password) {
        this(name, schemaPath, migrationsDir, jdbcUrl, username, password, "public");
    }

    public MigrateCreateCommand(String name, Path schemaPath, Path migrationsDir, String jdbcUrl, String username, String password, String dbSchema) {
        this.name = name;
        this.schemaPath = schemaPath;
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

        ModelRegistry registry = MigrateHelper.loadRegistry(schemaPath, err);
        if (registry == null) {
            return 1;
        }

        MigrationManager manager = new MigrationManager();
        try (Connection conn = MigrateHelper.openConnection(jdbcUrl, username, password, dbSchema)) {
            Optional<Path> generated = manager.generate(name, migrationsDir, registry, conn, dbSchema);
            if (generated.isPresent()) {
                out.println("✓ Generated migration: " + generated.get());
                return 0;
            } else {
                out.println("No schema changes detected. Database is already in sync with " + schemaPath);
                return 0;
            }
        } catch (Exception e) {
            err.println("Error generating migration: " + e.getMessage());
            return 1;
        }
    }
}

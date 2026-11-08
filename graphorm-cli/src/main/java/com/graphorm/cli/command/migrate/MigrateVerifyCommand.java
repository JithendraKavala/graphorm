package com.graphorm.cli.command.migrate;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.jdbc.ddl.TableDefinition;
import com.graphorm.jdbc.ddl.diff.DestructiveChange;
import com.graphorm.jdbc.ddl.diff.DestructiveChangeDetector;
import com.graphorm.jdbc.ddl.diff.SchemaDiff;
import com.graphorm.jdbc.ddl.diff.TableDiff;
import com.graphorm.jdbc.migration.MigrationManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Verifies whether the live database schema matches schema.graphorm (drift detection).
 */
@Command(
        name = "verify",
        description = "Verify database schema against schema.graphorm to detect drift",
        mixinStandardHelpOptions = true
)
public class MigrateVerifyCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(
            names = {"-s", "--schema"},
            description = "Path to the GraphORM schema file (default: ${DEFAULT-VALUE})",
            defaultValue = "schema.graphorm"
    )
    private Path schemaPath = Path.of("schema.graphorm");

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

    public MigrateVerifyCommand() {}

    public MigrateVerifyCommand(Path schemaPath, String jdbcUrl, String username, String password) {
        this(schemaPath, jdbcUrl, username, password, "public");
    }

    public MigrateVerifyCommand(Path schemaPath, String jdbcUrl, String username, String password, String dbSchema) {
        this.schemaPath = schemaPath;
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
            SchemaDiff diff = manager.verify(registry, conn, dbSchema);

            if (diff.isEmpty()) {
                out.println("✓ Database schema is in perfect sync with " + schemaPath + ". No drift detected.");
                return 0;
            }

            err.println("WARNING: Schema drift detected between database and " + schemaPath + "!");
            if (!diff.createdTables().isEmpty()) {
                err.println("Missing tables in DB (present in schema):");
                for (TableDefinition t : diff.createdTables()) {
                    err.println("  + " + t.name());
                }
            }
            if (!diff.droppedTables().isEmpty()) {
                err.println("Unexpected tables in DB (absent from schema):");
                for (TableDefinition t : diff.droppedTables()) {
                    err.println("  - " + t.name());
                }
            }
            if (!diff.alteredTables().isEmpty()) {
                err.println("Altered tables with differences:");
                for (TableDiff t : diff.alteredTables()) {
                    err.println("  ~ " + t.tableName());
                }
            }

            List<DestructiveChange> destructive = DestructiveChangeDetector.detect(diff);
            if (!destructive.isEmpty()) {
                err.println("\nPotential destructive differences:");
                for (DestructiveChange d : destructive) {
                    err.println("  ! [" + d.type() + "] " + d.description());
                }
            }

            return 2;
        } catch (Exception e) {
            err.println("Error verifying schema: " + e.getMessage());
            return 1;
        }
    }
}

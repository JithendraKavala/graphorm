package com.graphorm.cli.command.migrate;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.jdbc.ddl.DatabaseSchema;
import com.graphorm.jdbc.ddl.diff.MigrationPlan;
import com.graphorm.jdbc.ddl.diff.SchemaDiff;
import com.graphorm.jdbc.ddl.diff.SchemaDiffer;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
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
 * Calculates and prints the SQL DDL statements needed to align the database with schema.graphorm.
 */
@Command(
        name = "diff",
        description = "Print the SQL DDL diff between live database and schema.graphorm without creating a migration file",
        mixinStandardHelpOptions = true
)
public class MigrateDiffCommand implements Callable<Integer> {

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

    public MigrateDiffCommand() {}

    public MigrateDiffCommand(Path schemaPath, String jdbcUrl, String username, String password) {
        this(schemaPath, jdbcUrl, username, password, "public");
    }

    public MigrateDiffCommand(Path schemaPath, String jdbcUrl, String username, String password, String dbSchema) {
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

        try (Connection conn = MigrateHelper.openConnection(jdbcUrl, username, password, dbSchema)) {
            DatabaseSchema baseline = DatabaseSchema.fromCatalog(conn, dbSchema);
            DatabaseSchema target = DatabaseSchema.fromModelRegistry(registry);

            SchemaDiff diff = SchemaDiffer.diff(baseline, target);
            if (diff.isEmpty()) {
                out.println("-- Database schema matches " + schemaPath + ". No changes required.");
                return 0;
            }

            List<String> sqlStatements = MigrationPlan.generateSql(diff, new PostgreSqlDialect());
            for (String sql : sqlStatements) {
                out.println(sql);
                out.println();
            }
            return 0;
        } catch (Exception e) {
            err.println("Error generating diff: " + e.getMessage());
            return 1;
        }
    }
}

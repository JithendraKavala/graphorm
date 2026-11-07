package com.graphorm.cli.command;

import com.graphorm.cli.introspect.PostgresDatabaseIntrospector;
import com.graphorm.cli.introspect.TableInfo;
import com.graphorm.cli.introspect.SchemaEmitter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Reverse-engineers a PostgreSQL database schema into a GraphORM schema file.
 */
@Command(
        name = "introspect",
        description = "Reverse-engineer a PostgreSQL database schema into schema.graphorm",
        mixinStandardHelpOptions = true
)
public class IntrospectCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(
            names = {"--url"},
            description = "JDBC URL (e.g., jdbc:postgresql://localhost:5432/mydb). Defaults to DATABASE_URL env var."
    )
    private String jdbcUrl;

    @Option(
            names = {"-u", "--user"},
            description = "Database user (default: ${DEFAULT-VALUE})",
            defaultValue = "postgres"
    )
    private String username;

    @Option(
            names = {"-p", "--password"},
            description = "Database password (default: ${DEFAULT-VALUE})",
            defaultValue = "postgres"
    )
    private String password;

    @Option(
            names = {"-s", "--schema"},
            description = "PostgreSQL schema to introspect (default: ${DEFAULT-VALUE})",
            defaultValue = "public"
    )
    private String schemaName;

    @Option(
            names = {"-o", "--output"},
            description = "Target schema file path (default: ${DEFAULT-VALUE})",
            defaultValue = "schema.graphorm"
    )
    private Path outputPath;

    @Option(
            names = {"--force"},
            description = "Overwrite target file if present (default: ${DEFAULT-VALUE})",
            defaultValue = "false"
    )
    private boolean force;

    public IntrospectCommand() {}

    public IntrospectCommand(String jdbcUrl, String username, String password, String schemaName, Path outputPath, boolean force) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.schemaName = schemaName;
        this.outputPath = outputPath;
        this.force = force;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
        PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);

        String effectiveUrl = jdbcUrl;
        if (effectiveUrl == null || effectiveUrl.isBlank()) {
            effectiveUrl = System.getenv("DATABASE_URL");
        }

        if (effectiveUrl == null || effectiveUrl.isBlank()) {
            err.println("Error: Database connection URL is required. Provide --url or set the DATABASE_URL environment variable.");
            return 1;
        }

        if (outputPath == null) {
            outputPath = Path.of("schema.graphorm");
        }

        if (!force && Files.exists(outputPath)) {
            err.println("Error: Target schema file already exists: " + outputPath + ". Use --force to overwrite.");
            return 1;
        }

        try {
            out.println("Connecting to database: " + effectiveUrl);
            out.println("Introspecting schema '" + schemaName + "'...");

            PostgresDatabaseIntrospector introspector = new PostgresDatabaseIntrospector(
                    effectiveUrl, username, password, schemaName
            );

            List<TableInfo> tables = introspector.introspectTables();
            String schemaContent = SchemaEmitter.emit(tables, "postgresql");

            Path parentDir = outputPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Files.writeString(outputPath, schemaContent, StandardCharsets.UTF_8);

            long modelCount = tables.stream().filter(t -> !t.isView()).count();
            long viewCount = tables.stream().filter(TableInfo::isView).count();

            out.println("Successfully introspected database into '" + outputPath + "'!");
            out.printf("  Found %d model(s) and %d view(s).%n", modelCount, viewCount);
            out.println();
            out.println("Next steps:");
            out.println("  1. Review and refine " + outputPath);
            out.println("  2. Run 'graphorm validate -s " + outputPath + "'");
            out.println("  3. Run 'graphorm generate -s " + outputPath + "'");

            return 0;
        } catch (SQLException e) {
            err.println("Database introspection failed: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            err.println("Failed to write output schema file: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            err.println("Unexpected error during introspection: " + e.getMessage());
            return 1;
        }
    }
}

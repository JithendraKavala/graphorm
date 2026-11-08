package com.graphorm.cli.command.migrate;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.schema.ast.SchemaDocument;
import com.graphorm.schema.parser.GraphOrmParserFacade;
import com.graphorm.schema.transformer.SchemaToMetadataTransformer;
import com.graphorm.schema.validation.Diagnostic;
import com.graphorm.schema.validation.SchemaValidator;
import com.graphorm.schema.validation.ValidationResult;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Shared utility methods for migrate CLI subcommands.
 */
public final class MigrateHelper {

    private MigrateHelper() {}

    public static String resolveJdbcUrl(String explicitUrl) {
        if (explicitUrl != null && !explicitUrl.isBlank()) {
            return explicitUrl;
        }
        String envUrl = System.getenv("DATABASE_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            return envUrl;
        }
        return "jdbc:postgresql://localhost:5432/postgres";
    }

    public static Connection openConnection(String explicitUrl, String username, String password) throws SQLException {
        return openConnection(explicitUrl, username, password, null);
    }

    public static Connection openConnection(String explicitUrl, String username, String password, String dbSchema) throws SQLException {
        String url = resolveJdbcUrl(explicitUrl);
        String user = username != null ? username : "postgres";
        String pass = password != null ? password : "";
        Connection conn = DriverManager.getConnection(url, user, pass);
        if (dbSchema != null && !dbSchema.isBlank()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET search_path TO \"" + dbSchema.replace("\"", "\"\"") + "\", public;");
            }
        }
        return conn;
    }

    public static ModelRegistry loadRegistry(Path schemaPath, PrintWriter err) {
        if (schemaPath == null || !Files.exists(schemaPath)) {
            err.println("Error: Schema file does not exist: " + schemaPath);
            return null;
        }

        String source;
        try {
            source = Files.readString(schemaPath, StandardCharsets.UTF_8);
        } catch (Exception e) {
            err.println("Error reading schema file: " + e.getMessage());
            return null;
        }

        String fileName = schemaPath.getFileName().toString();
        GraphOrmParserFacade.ParseResult parseResult = GraphOrmParserFacade.parseWithDiagnostics(source, fileName);
        if (parseResult.hasErrors()) {
            err.println("Error: Schema has syntax error(s):");
            for (Diagnostic d : parseResult.diagnostics()) {
                err.print(d.formatWithCaret(source));
            }
            return null;
        }

        SchemaDocument document = parseResult.document();
        ValidationResult valResult = SchemaValidator.validate(document);
        if (valResult.hasErrors()) {
            err.println("Error: Schema has validation error(s):");
            for (Diagnostic d : valResult.getErrors()) {
                err.print(d.formatWithCaret(source));
            }
            return null;
        }

        return SchemaToMetadataTransformer.transform(document);
    }
}

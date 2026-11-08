package com.graphorm.cli.command;

import com.graphorm.cli.GraphOrmCli;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FormatCommandTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("fmt --check exits 1 when schema is unformatted and 0 when formatted")
    void fmtCheckDetectsUnformatted() throws Exception {
        Path schemaFile = tempDir.resolve("schema.graphorm");
        String unformatted = """
                datasource db {
                provider = "postgresql"
                url = env("DATABASE_URL")
                }
                model User {
                id String @id
                name String
                }
                """;
        Files.writeString(schemaFile, unformatted, StandardCharsets.UTF_8);

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "fmt", "-s", schemaFile.toString(), "--check");

        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString()).contains("needs formatting");

        // Now format with -w
        out = new StringWriter();
        err = new StringWriter();
        exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "fmt", "-s", schemaFile.toString(), "-w");
        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("Formatted");

        // Verify with --check again
        out = new StringWriter();
        err = new StringWriter();
        exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "fmt", "-s", schemaFile.toString(), "--check");
        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("canonically formatted");
    }

    @Test
    @DisplayName("fmt prints formatted schema to stdout by default")
    void fmtPrintsToStdout() throws Exception {
        Path schemaFile = tempDir.resolve("schema.graphorm");
        String unformatted = """
                datasource db {
                provider = "postgresql"
                url = env("DATABASE_URL")
                }
                model User {
                id String @id
                }
                """;
        Files.writeString(schemaFile, unformatted, StandardCharsets.UTF_8);

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "fmt", "-s", schemaFile.toString());

        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("  provider = \"postgresql\"");
        assertThat(out.toString()).contains("id  String  @id");
    }
}

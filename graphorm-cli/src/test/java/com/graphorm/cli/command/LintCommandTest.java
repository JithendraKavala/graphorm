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

class LintCommandTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("lint reports diagnostics and exits 0 in non-strict mode for warnings")
    void lintReportsWarnings() throws Exception {
        Path schemaFile = tempDir.resolve("schema.graphorm");
        String schema = """
                datasource db {
                  provider = "postgresql"
                  url      = env("DATABASE_URL")
                }

                model user_profile {
                  id String @id
                }
                """;
        Files.writeString(schemaFile, schema, StandardCharsets.UTF_8);

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "lint", "-s", schemaFile.toString());

        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("[WARN]");
        assertThat(out.toString()).contains("naming-convention");
    }

    @Test
    @DisplayName("lint --strict exits 1 when warnings are present")
    void lintStrictExitsOneOnWarnings() throws Exception {
        Path schemaFile = tempDir.resolve("schema.graphorm");
        String schema = """
                datasource db {
                  provider = "postgresql"
                  url      = env("DATABASE_URL")
                }

                model user_profile {
                  id String @id
                }
                """;
        Files.writeString(schemaFile, schema, StandardCharsets.UTF_8);

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "lint", "-s", schemaFile.toString(), "--strict");

        assertThat(exitCode).isEqualTo(1);
    }

    @Test
    @DisplayName("lint exits 0 when schema is completely clean")
    void lintCleanSchema() throws Exception {
        Path schemaFile = tempDir.resolve("schema.graphorm");
        String schema = """
                datasource db {
                  provider = "postgresql"
                  url      = env("DATABASE_URL")
                }

                model User {
                  id        String @id
                  firstName String
                }
                """;
        Files.writeString(schemaFile, schema, StandardCharsets.UTF_8);

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "lint", "-s", schemaFile.toString());

        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("0 warnings/errors");
    }
}

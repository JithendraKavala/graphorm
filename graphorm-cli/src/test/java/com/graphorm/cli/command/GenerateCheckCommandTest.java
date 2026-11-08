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

class GenerateCheckCommandTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("generate --check detects missing and outdated generated files")
    void checkDetectsStaleFiles() throws Exception {
        Path schemaFile = tempDir.resolve("schema.graphorm");
        Path outputDir = tempDir.resolve("generated");

        String schema = """
                datasource db {
                  provider = "postgresql"
                  url      = env("DATABASE_URL")
                }

                generator client {
                  provider = "graphorm-client-java"
                  package  = "com.example.app"
                }

                model Customer {
                  id    String @id
                  name  String
                }
                """;
        Files.writeString(schemaFile, schema, StandardCharsets.UTF_8);

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        // 1. Initially nothing generated: --check should exit 1
        int code = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "generate", "-s", schemaFile.toString(), "-o", outputDir.toString(), "--check");
        assertThat(code).isEqualTo(1);
        assertThat(err.toString()).contains("stale");

        // 2. Run normal generation
        out = new StringWriter();
        err = new StringWriter();
        code = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "generate", "-s", schemaFile.toString(), "-o", outputDir.toString());
        assertThat(code).isEqualTo(0);

        // 3. Run --check: should pass cleanly (exit 0)
        out = new StringWriter();
        err = new StringWriter();
        code = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "generate", "-s", schemaFile.toString(), "-o", outputDir.toString(), "--check");
        assertThat(code).isEqualTo(0);
        assertThat(out.toString()).contains("up to date with schema");

        // 4. Tamper with a generated file: should detect outdated and exit 1
        Path customerModel = outputDir.resolve("com/example/app/model/Customer.java");
        assertThat(Files.exists(customerModel)).isTrue();
        Files.writeString(customerModel, "// altered content", StandardCharsets.UTF_8);

        out = new StringWriter();
        err = new StringWriter();
        code = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "generate", "-s", schemaFile.toString(), "-o", outputDir.toString(), "--verify");
        assertThat(code).isEqualTo(1);
        assertThat(err.toString()).contains("Outdated generated file");
    }
}

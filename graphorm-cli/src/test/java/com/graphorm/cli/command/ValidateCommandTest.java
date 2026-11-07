package com.graphorm.cli.command;

import com.graphorm.cli.GraphOrmCli;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TASK-14.2: ValidateCommand Unit Tests")
class ValidateCommandTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should display help for validate subcommand")
    void shouldDisplayValidateHelp() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err), "validate", "--help");

        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString())
                .contains("Validate a GraphORM schema file")
                .contains("--schema");
    }

    @Test
    @DisplayName("Should validate valid schema file and return exit code 0")
    void shouldValidateValidSchema() throws Exception {
        String schema = """
                datasource db {
                  provider = "postgresql"
                  url      = env("DATABASE_URL")
                }

                generator client {
                  provider = "graphorm-client-java"
                }

                model User {
                  id    Int    @id @default(autoincrement())
                  email String @unique
                  posts Post[]
                }

                model Post {
                  id       Int    @id @default(autoincrement())
                  title    String
                  authorId Int
                  author   User   @relation(fields: [authorId], references: [id])
                }

                view UserStats {
                  userId    Int    @id
                  postCount Int
                  @@view
                  @@map("user_stats")
                }
                """;

        Path schemaFile = tempDir.resolve("valid_schema.graphorm");
        Files.writeString(schemaFile, schema);

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err), "validate", "-s", schemaFile.toString());

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output)
                .contains("valid_schema.graphorm' is valid!")
                .contains("2 model(s)")
                .contains("1 view(s)");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    @DisplayName("Should fail with exit code 1 when schema file does not exist")
    void shouldFailWhenSchemaNotFound() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        Path missing = tempDir.resolve("does_not_exist.graphorm");
        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err), "validate", "-s", missing.toString());

        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString()).contains("Error: Schema file not found");
    }

    @Test
    @DisplayName("Should fail with exit code 1 when schema path is a directory")
    void shouldFailWhenSchemaIsDirectory() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err), "validate", "-s", tempDir.toString());

        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString()).contains("Error: Schema path is a directory");
    }

    @Test
    @DisplayName("Should fail with exit code 1 and format syntax error diagnostics with caret")
    void shouldFailOnSyntaxError() throws Exception {
        String badSchema = """
                model User {
                  id Int @id
                  name
                }
                """;

        Path schemaFile = tempDir.resolve("syntax_error.graphorm");
        Files.writeString(schemaFile, badSchema);

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err), "validate", "-s", schemaFile.toString());

        assertThat(exitCode).isEqualTo(1);
        String errorOutput = err.toString();
        assertThat(errorOutput)
                .contains("Schema validation failed with syntax error(s):")
                .contains("^");
    }

    @Test
    @DisplayName("Should fail with exit code 1 and format semantic validation diagnostics")
    void shouldFailOnSemanticError() throws Exception {
        String semanticErrorSchema = """
                model Student {
                  id           Int        @id
                  departmentId Int
                  department   Department @relation(fields: [departmentId], references: [id])
                }
                // Missing Department model!
                """;

        Path schemaFile = tempDir.resolve("semantic_error.graphorm");
        Files.writeString(schemaFile, semanticErrorSchema);

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err), "validate", "-s", schemaFile.toString());

        assertThat(exitCode).isEqualTo(1);
        String errorOutput = err.toString();
        assertThat(errorOutput)
                .contains("Schema validation failed with semantic error(s):")
                .contains("Department");
    }
}

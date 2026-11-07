package com.graphorm.cli.command;

import com.graphorm.cli.GraphOrmCli;
import com.graphorm.generator.compiler.InMemoryCompiler;
import com.graphorm.generator.engine.GeneratedSourceFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TASK-14.3: GenerateCommand Unit & Compiler Tests")
class GenerateCommandTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should display help for generate subcommand")
    void shouldDisplayGenerateHelp() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err), "generate", "--help");

        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString())
                .contains("Generate Java models, views")
                .contains("--schema")
                .contains("--output")
                .contains("--package")
                .contains("--clean");
    }

    @Test
    @DisplayName("Should generate compilable Java sources from valid schema")
    void shouldGenerateCompilableJavaSources() throws Exception {
        String schema = """
                datasource db {
                  provider = "postgresql"
                  url      = env("DATABASE_URL")
                }

                generator client {
                  provider = "graphorm-client-java"
                  package  = "com.test.school"
                }

                enum Status {
                  ACTIVE
                  INACTIVE
                }

                model Department {
                  id       Int       @id @default(autoincrement())
                  name     String
                  students Student[]
                }

                model Student {
                  id           Int        @id @default(autoincrement())
                  name         String
                  status       Status     @default(ACTIVE)
                  departmentId Int
                  department   Department @relation(fields: [departmentId], references: [id])
                }

                view DepartmentOverview {
                  id           Int    @id
                  deptName     String
                  studentCount Int

                  @@view
                  @@map("vw_dept_overview")
                }
                """;

        Path schemaFile = tempDir.resolve("school.graphorm");
        Files.writeString(schemaFile, schema);

        Path outputDir = tempDir.resolve("generated");

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "generate",
                "-s", schemaFile.toString(),
                "-o", outputDir.toString()
        );

        assertThat(exitCode).isEqualTo(0);
        assertThat(err.toString()).isEmpty();
        assertThat(out.toString())
                .contains("Successfully generated")
                .contains("Java source file(s)")
                .contains("Base package: com.test.school");

        // Collect all generated files from disk
        List<GeneratedSourceFile> generatedFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(outputDir)) {
            List<Path> javaPaths = stream.filter(p -> p.toString().endsWith(".java")).toList();
            assertThat(javaPaths).isNotEmpty();

            for (Path jp : javaPaths) {
                Path relative = outputDir.relativize(jp);
                String fileName = jp.getFileName().toString();
                String className = fileName.substring(0, fileName.length() - 5);
                String packageName = relative.getParent() != null ? relative.getParent().toString().replace('/', '.') : "";
                String content = Files.readString(jp, StandardCharsets.UTF_8);
                generatedFiles.add(new GeneratedSourceFile(packageName, className, content));
            }
        }

        // Verify compilation of all generated files in-memory
        InMemoryCompiler.CompilationResult result = InMemoryCompiler.compile(generatedFiles);
        result.assertCleanCompilation();
        assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("Should allow overriding package with --package option")
    void shouldAllowOverridingPackage() throws Exception {
        String schema = """
                model User {
                  id Int @id
                }
                """;
        Path schemaFile = tempDir.resolve("simple.graphorm");
        Files.writeString(schemaFile, schema);

        Path outputDir = tempDir.resolve("out_pkg");

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "generate",
                "-s", schemaFile.toString(),
                "-o", outputDir.toString(),
                "-p", "com.custom.pkg"
        );

        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("Base package: com.custom.pkg");
        assertThat(Files.exists(outputDir.resolve("com/custom/pkg/model/User.java"))).isTrue();
    }

    @Test
    @DisplayName("Should clean directory when --clean is provided")
    void shouldCleanOutputDirectory() throws Exception {
        String schema = """
                model Item {
                  id Int @id
                }
                """;
        Path schemaFile = tempDir.resolve("item.graphorm");
        Files.writeString(schemaFile, schema);

        Path outputDir = tempDir.resolve("clean_test");
        Files.createDirectories(outputDir);
        Path staleFile = outputDir.resolve("StaleFile.txt");
        Files.writeString(staleFile, "stale content");
        assertThat(Files.exists(staleFile)).isTrue();

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "generate",
                "-s", schemaFile.toString(),
                "-o", outputDir.toString(),
                "--clean"
        );

        assertThat(exitCode).isEqualTo(0);
        assertThat(Files.exists(staleFile)).isFalse();
    }

    @Test
    @DisplayName("Should fail code generation if schema has errors")
    void shouldFailIfSchemaHasErrors() throws Exception {
        String badSchema = "model Broken {";
        Path schemaFile = tempDir.resolve("broken.graphorm");
        Files.writeString(schemaFile, badSchema);

        Path outputDir = tempDir.resolve("should_not_exist");

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "generate",
                "-s", schemaFile.toString(),
                "-o", outputDir.toString()
        );

        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString()).contains("Cannot generate code");
    }
}

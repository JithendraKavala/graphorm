package com.graphorm.maven.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerateMojoTest {

    @TempDir
    Path tempDir;

    private MavenProject project;
    private List<String> compileSourceRoots;
    private List<String> testCompileSourceRoots;

    private static final String VALID_SCHEMA = """
            datasource db {
              provider = "postgresql"
              url      = "postgresql://localhost:5432/test"
            }

            generator client {
              provider = "graphorm-client-java"
              package  = "com.example.university"
            }

            model Department {
              id   Int    @id @default(autoincrement())
              name String
            }

            model Student {
              id           Int        @id @default(autoincrement())
              name         String
              departmentId Int
              department   Department @relation(fields: [departmentId], references: [id])
            }
            """;

    @BeforeEach
    void setUp() {
        compileSourceRoots = new ArrayList<>();
        testCompileSourceRoots = new ArrayList<>();

        project = new MavenProject() {
            @Override
            public void addCompileSourceRoot(String path) {
                compileSourceRoots.add(path);
            }

            @Override
            public List<String> getCompileSourceRoots() {
                return compileSourceRoots;
            }

            @Override
            public void addTestCompileSourceRoot(String path) {
                testCompileSourceRoots.add(path);
            }

            @Override
            public List<String> getTestCompileSourceRoots() {
                return testCompileSourceRoots;
            }

            @Override
            public File getBasedir() {
                return tempDir.toFile();
            }

            @Override
            public String getGroupId() {
                return "com.example";
            }
        };
    }

    @Test
    void shouldGenerateJavaSourcesSuccessfullyAndAddSourceRoot() throws Exception {
        Path schemaPath = tempDir.resolve("schema.graphorm");
        Files.writeString(schemaPath, VALID_SCHEMA);

        Path outputDir = tempDir.resolve("target/generated-sources/graphorm");

        GenerateMojo mojo = new GenerateMojo();
        mojo.setProject(project);
        mojo.setSchemaFile(schemaPath.toFile());
        mojo.setOutputDirectory(outputDir.toFile());
        mojo.setAddSource(true);
        mojo.setClean(true);

        mojo.execute();

        assertThat(Files.exists(outputDir)).isTrue();
        assertThat(outputDir.resolve("com/example/university/model/Student.java")).exists();
        assertThat(outputDir.resolve("com/example/university/model/Department.java")).exists();
        assertThat(outputDir.resolve("com/example/university/client/GraphOrmClient.java")).exists();

        assertThat(compileSourceRoots)
                .contains(outputDir.toAbsolutePath().normalize().toString());
    }

    @Test
    void shouldSkipGenerationWhenSkipIsTrue() throws Exception {
        Path schemaPath = tempDir.resolve("schema.graphorm");
        Files.writeString(schemaPath, VALID_SCHEMA);

        Path outputDir = tempDir.resolve("target/generated-sources/graphorm");

        GenerateMojo mojo = new GenerateMojo();
        mojo.setProject(project);
        mojo.setSchemaFile(schemaPath.toFile());
        mojo.setOutputDirectory(outputDir.toFile());
        mojo.setSkip(true);

        mojo.execute();

        assertThat(Files.exists(outputDir)).isFalse();
        assertThat(compileSourceRoots).isEmpty();
    }

    @Test
    void shouldThrowMojoExecutionExceptionOnSyntaxError() throws IOException {
        Path schemaPath = tempDir.resolve("broken.graphorm");
        Files.writeString(schemaPath, "model Student { broken syntax");

        Path outputDir = tempDir.resolve("target/generated-sources/graphorm");

        GenerateMojo mojo = new GenerateMojo();
        mojo.setProject(project);
        mojo.setSchemaFile(schemaPath.toFile());
        mojo.setOutputDirectory(outputDir.toFile());

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("syntax error");
    }

    @Test
    void shouldThrowMojoExecutionExceptionOnSemanticError() throws IOException {
        Path schemaPath = tempDir.resolve("invalid.graphorm");
        Files.writeString(schemaPath, """
                model Student {
                  name String
                }
                """); // missing @id

        Path outputDir = tempDir.resolve("target/generated-sources/graphorm");

        GenerateMojo mojo = new GenerateMojo();
        mojo.setProject(project);
        mojo.setSchemaFile(schemaPath.toFile());
        mojo.setOutputDirectory(outputDir.toFile());

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("validation error");
    }

    @Test
    void shouldExecuteTestGenerateMojoAndAddTestSourceRoot() throws Exception {
        Path schemaPath = tempDir.resolve("test-schema.graphorm");
        Files.writeString(schemaPath, VALID_SCHEMA);

        Path outputDir = tempDir.resolve("target/generated-test-sources/graphorm");

        TestGenerateMojo mojo = new TestGenerateMojo();
        mojo.setProject(project);
        mojo.setTestSchemaFile(schemaPath.toFile());
        mojo.setTestOutputDirectory(outputDir.toFile());
        mojo.setAddTestSources(true);

        mojo.execute();

        assertThat(Files.exists(outputDir)).isTrue();
        assertThat(testCompileSourceRoots)
                .contains(outputDir.toAbsolutePath().normalize().toString());
    }

    @Test
    void shouldExecuteValidateMojoSuccessfully() throws Exception {
        Path schemaPath = tempDir.resolve("schema.graphorm");
        Files.writeString(schemaPath, VALID_SCHEMA);

        ValidateMojo mojo = new ValidateMojo();
        mojo.setProject(project);
        mojo.setSchemaFile(schemaPath.toFile());

        mojo.execute(); // should not throw
    }
}

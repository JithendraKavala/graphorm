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

@DisplayName("TASK-14.4: InitCommand Unit Tests")
class InitCommandTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should display help for init subcommand")
    void shouldDisplayInitHelp() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err), "init", "--help");

        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString())
                .contains("Initialize a new GraphORM project")
                .contains("--dir")
                .contains("--package")
                .contains("--dialect")
                .contains("--force");
    }

    @Test
    @DisplayName("Should scaffold starter project files in target directory")
    void shouldScaffoldStarterProject() {
        Path projectDir = tempDir.resolve("my-app");

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "init",
                "-d", projectDir.toString(),
                "-p", "com.mycompany.app"
        );

        assertThat(exitCode).isEqualTo(0);
        assertThat(err.toString()).isEmpty();
        assertThat(out.toString())
                .contains("Initialized GraphORM starter project")
                .contains("Created files:")
                .contains("Next steps:");

        Path schemaFile = projectDir.resolve("schema.graphorm");
        Path propFile = projectDir.resolve("graphorm.properties");

        assertThat(Files.exists(schemaFile)).isTrue();
        assertThat(Files.exists(propFile)).isTrue();

        // Verify the starter schema passes validate command cleanly!
        StringWriter validateOut = new StringWriter();
        StringWriter validateErr = new StringWriter();
        int valCode = GraphOrmCli.execute(new PrintWriter(validateOut), new PrintWriter(validateErr),
                "validate", "-s", schemaFile.toString()
        );

        assertThat(valCode).isEqualTo(0);
        assertThat(validateOut.toString()).contains("is valid!");
        assertThat(validateErr.toString()).isEmpty();
    }

    @Test
    @DisplayName("Should fail when files exist and --force is not specified")
    void shouldFailWhenFilesExistWithoutForce() throws Exception {
        Path projectDir = tempDir.resolve("existing-app");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("schema.graphorm"), "// existing");

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "init",
                "-d", projectDir.toString()
        );

        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString()).contains("Error: File already exists");
    }

    @Test
    @DisplayName("Should overwrite existing files when --force is specified")
    void shouldOverwriteWithForce() throws Exception {
        Path projectDir = tempDir.resolve("force-app");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("schema.graphorm"), "// old content");

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err),
                "init",
                "-d", projectDir.toString(),
                "--force"
        );

        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("Initialized GraphORM starter project");
        String content = Files.readString(projectDir.resolve("schema.graphorm"));
        assertThat(content).contains("model User");
    }
}

package com.graphorm.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TASK-14.1: GraphOrmCli Entrypoint & Help Tests")
class GraphOrmCliTest {

    @Test
    @DisplayName("Should display ASCII banner and usage help when executed with --help")
    void shouldDisplayBannerAndUsageOnHelp() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err), "--help");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();

        assertThat(output)
                .contains("GraphORM")
                .contains("Modern Data & GraphQL Framework for Java")
                .contains("Usage: graphorm")
                .contains("validate")
                .contains("generate")
                .contains("init")
                .contains("introspect");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    @DisplayName("Should display ASCII banner and usage help when executed with -h")
    void shouldDisplayBannerAndUsageOnShortHelp() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err), "-h");

        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("Usage: graphorm");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    @DisplayName("Should display version info when executed with --version")
    void shouldDisplayVersionOnVersionFlag() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err), "--version");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("GraphORM CLI version");
        assertThat(output).contains("JVM:");
        assertThat(output).contains("OS:");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    @DisplayName("Should display usage when executed with no arguments")
    void shouldDisplayUsageWhenNoArgs() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err));

        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("Usage: graphorm");
    }

    @Test
    @DisplayName("Should return error exit code on unknown option or command")
    void shouldFailOnUnknownOption() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err), "--non-existent-flag");

        assertThat(exitCode).isNotEqualTo(0);
        assertThat(err.toString()).contains("Unknown option");
    }
}

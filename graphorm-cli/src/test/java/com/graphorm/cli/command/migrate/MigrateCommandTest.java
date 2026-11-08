package com.graphorm.cli.command.migrate;

import com.graphorm.cli.GraphOrmCli;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class MigrateCommandTest {

    @Test
    @DisplayName("Should display migrate command usage and subcommands")
    void shouldDisplayMigrateUsage() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err), "migrate", "--help");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("create");
        assertThat(output).contains("apply");
        assertThat(output).contains("status");
        assertThat(output).contains("verify");
        assertThat(output).contains("diff");
    }

    @Test
    @DisplayName("Should display help for migrate create")
    void shouldDisplayMigrateCreateHelp() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err), "migrate", "create", "--help");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("--schema");
        assertThat(output).contains("--migrations-dir");
    }

    @Test
    @DisplayName("Should display help for migrate apply")
    void shouldDisplayMigrateApplyHelp() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err), "migrate", "apply", "--help");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("--migrations-dir");
        assertThat(output).contains("--url");
    }

    @Test
    @DisplayName("Should display help for migrate status")
    void shouldDisplayMigrateStatusHelp() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err), "migrate", "status", "--help");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("--migrations-dir");
    }

    @Test
    @DisplayName("Should display help for migrate verify")
    void shouldDisplayMigrateVerifyHelp() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err), "migrate", "verify", "--help");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("--schema");
    }

    @Test
    @DisplayName("Should display help for migrate diff")
    void shouldDisplayMigrateDiffHelp() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = GraphOrmCli.execute(new PrintWriter(out), new PrintWriter(err), "migrate", "diff", "--help");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output).contains("--schema");
    }
}

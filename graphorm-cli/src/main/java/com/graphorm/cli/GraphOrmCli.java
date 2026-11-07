package com.graphorm.cli;

import com.graphorm.cli.command.GenerateCommand;
import com.graphorm.cli.command.InitCommand;
import com.graphorm.cli.command.IntrospectCommand;
import com.graphorm.cli.command.ValidateCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

/**
 * Main command-line interface entry point for GraphORM.
 * <p>
 * Supported subcommands:
 * <ul>
 *     <li>{@code validate} - Validates schema file and outputs formatted diagnostics</li>
 *     <li>{@code generate} - Generates Java models, views, input DTOs, and client API</li>
 *     <li>{@code init} - Scaffolds a new starter project with sample schema</li>
 *     <li>{@code introspect} - Reverse-engineers PostgreSQL database schema into schema.graphorm</li>
 * </ul>
 */
@Command(
        name = "graphorm",
        description = "GraphORM CLI - Modern Data & GraphQL Framework for Java",
        header = {
                "   ____                 _      ___  ____  __  __ ",
                "  / ___|_ __ __ _ _ __ | |__  / _ \\|  _ \\|  \\/  |",
                " | |  _| '__/ _` | '_ \\| '_ \\| | | | |_) | |\\/| |",
                " | |_| | | | (_| | |_) | | | | |_| |  _ <| |  | |",
                "  \\____|_|  \\__,_| .__/|_| |_|\\___/|_| \\_\\_|  |_|",
                "                 |_|                             ",
                "  Modern Data & GraphQL Framework for Java",
                ""
        },
        mixinStandardHelpOptions = true,
        versionProvider = GraphOrmVersionProvider.class,
        subcommands = {
                ValidateCommand.class,
                GenerateCommand.class,
                InitCommand.class,
                IntrospectCommand.class,
                HelpCommand.class
        }
)
public class GraphOrmCli implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return 0;
    }

    /**
     * Builds and configures a {@link CommandLine} instance for GraphORM.
     */
    public static CommandLine createCommandLine() {
        return createCommandLine(new PrintWriter(System.out, true), new PrintWriter(System.err, true));
    }

    /**
     * Builds and configures a {@link CommandLine} instance with custom output/error streams.
     */
    public static CommandLine createCommandLine(PrintWriter out, PrintWriter err) {
        CommandLine cmd = new CommandLine(new GraphOrmCli());
        cmd.setOut(out);
        cmd.setErr(err);
        cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
            commandLine.getErr().println("Error: " + ex.getMessage());
            return 1;
        });
        return cmd;
    }

    /**
     * Executes the CLI with standard System streams and exits the JVM.
     */
    public static void main(String[] args) {
        int exitCode = execute(args);
        System.exit(exitCode);
    }

    /**
     * Executes the CLI and returns the exit code without terminating the JVM.
     */
    public static int execute(String... args) {
        CommandLine cmd = createCommandLine();
        return cmd.execute(args);
    }

    /**
     * Executes the CLI with custom output writers and returns the exit code.
     */
    public static int execute(PrintWriter out, PrintWriter err, String... args) {
        CommandLine cmd = createCommandLine(out, err);
        return cmd.execute(args);
    }
}

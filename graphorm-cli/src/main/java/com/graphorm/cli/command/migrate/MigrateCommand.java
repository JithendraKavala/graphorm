package com.graphorm.cli.command.migrate;

import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

/**
 * Top-level CLI command for database schema migrations.
 */
@Command(
        name = "migrate",
        description = "Database schema migrations (diff, create, apply, status, verify)",
        mixinStandardHelpOptions = true,
        subcommands = {
                MigrateCreateCommand.class,
                MigrateApplyCommand.class,
                MigrateStatusCommand.class,
                MigrateVerifyCommand.class,
                MigrateDiffCommand.class,
                HelpCommand.class
        }
)
public class MigrateCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return 0;
    }
}

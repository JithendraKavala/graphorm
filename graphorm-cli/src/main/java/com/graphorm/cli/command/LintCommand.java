package com.graphorm.cli.command;

import com.graphorm.schema.lint.LintDiagnostic;
import com.graphorm.schema.lint.LintResult;
import com.graphorm.schema.lint.LintSeverity;
import com.graphorm.schema.lint.SchemaLinter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Lints a GraphORM schema for naming conventions, best practices, and missing indexes.
 */
@Command(
        name = "lint",
        description = "Lint schema for naming conventions, best practices, and missing foreign key indexes",
        mixinStandardHelpOptions = true
)
public class LintCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(
            names = {"-s", "--schema"},
            description = "Path to the GraphORM schema file (default: ${DEFAULT-VALUE})",
            defaultValue = "schema.graphorm"
    )
    private Path schemaPath = Path.of("schema.graphorm");

    @Option(
            names = {"--strict"},
            description = "Fail with exit code 1 if any warnings are detected (default: false)"
    )
    private boolean strict = false;

    public LintCommand() {}

    public LintCommand(Path schemaPath, boolean strict) {
        this.schemaPath = schemaPath;
        this.strict = strict;
    }

    public void setSchemaPath(Path schemaPath) {
        this.schemaPath = schemaPath;
    }

    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
        PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);

        if (!Files.exists(schemaPath)) {
            err.println("Error: Schema file does not exist: " + schemaPath);
            return 1;
        }

        String source;
        try {
            source = Files.readString(schemaPath, StandardCharsets.UTF_8);
        } catch (Exception e) {
            err.println("Error reading schema file: " + e.getMessage());
            return 1;
        }

        LintResult result;
        try {
            result = SchemaLinter.lint(source);
        } catch (Exception e) {
            err.println("Linting failed: " + e.getMessage());
            return 1;
        }

        if (result.isClean()) {
            out.println("Schema linting passed cleanly with 0 warnings/errors: " + schemaPath);
            return 0;
        }

        for (LintDiagnostic diag : result.diagnostics()) {
            String prefix = diag.severity() == LintSeverity.ERROR ? "ERROR" : "WARN";
            PrintWriter target = diag.severity() == LintSeverity.ERROR ? err : out;
            target.printf("[%s] %s:%d:%d [%s] %s%n",
                    prefix,
                    schemaPath.getFileName(),
                    diag.position().line(),
                    diag.position().column(),
                    diag.ruleName(),
                    diag.message()
            );
        }

        out.printf("%nFound %d diagnostic(s) (%d error(s), %d warning(s))%n",
                result.diagnostics().size(),
                result.getErrors().size(),
                result.getWarnings().size()
        );

        if (result.hasErrors() || (strict && !result.isClean())) {
            return 1;
        }

        return 0;
    }
}

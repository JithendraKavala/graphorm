package com.graphorm.cli.command;

import com.graphorm.schema.ast.SchemaDocument;
import com.graphorm.schema.parser.GraphOrmParserFacade;
import com.graphorm.schema.validation.Diagnostic;
import com.graphorm.schema.validation.SchemaValidator;
import com.graphorm.schema.validation.ValidationResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Validates a GraphORM schema file and outputs formatted diagnostics.
 */
@Command(
        name = "validate",
        description = "Validate a GraphORM schema file and print diagnostics",
        mixinStandardHelpOptions = true
)
public class ValidateCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(
            names = {"-s", "--schema"},
            description = "Path to the GraphORM schema file (default: ${DEFAULT-VALUE})",
            defaultValue = "schema.graphorm"
    )
    private Path schemaPath;

    public ValidateCommand() {}

    public ValidateCommand(Path schemaPath) {
        this.schemaPath = schemaPath;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
        PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);

        if (schemaPath == null || !Files.exists(schemaPath)) {
            err.println("Error: Schema file not found: " + schemaPath);
            return 1;
        }

        if (Files.isDirectory(schemaPath)) {
            err.println("Error: Schema path is a directory, not a file: " + schemaPath);
            return 1;
        }

        String source;
        try {
            source = Files.readString(schemaPath, StandardCharsets.UTF_8);
        } catch (Exception e) {
            err.println("Error reading schema file: " + e.getMessage());
            return 1;
        }

        String fileName = schemaPath.getFileName().toString();
        GraphOrmParserFacade.ParseResult parseResult = GraphOrmParserFacade.parseWithDiagnostics(source, fileName);

        if (parseResult.hasErrors()) {
            err.println("Schema validation failed with syntax error(s):");
            for (Diagnostic d : parseResult.diagnostics()) {
                err.print(d.formatWithCaret(source));
            }
            return 1;
        }

        SchemaDocument document = parseResult.document();
        ValidationResult validationResult = SchemaValidator.validate(document);

        if (validationResult.hasErrors()) {
            err.println("Schema validation failed with semantic error(s):");
            List<Diagnostic> errors = validationResult.getErrors();
            for (Diagnostic d : errors) {
                err.print(d.formatWithCaret(source));
            }
            return 1;
        }

        // Output warnings if any
        List<Diagnostic> warnings = validationResult.getWarnings();
        if (!warnings.isEmpty()) {
            out.println("Validation succeeded with warning(s):");
            for (Diagnostic w : warnings) {
                out.print(w.formatWithCaret(source));
            }
        }

        out.println("Schema '" + schemaPath + "' is valid!");
        out.printf("  Summary: %d model(s), %d view(s), %d composite type(s), %d enum(s)%n",
                document.models().size(),
                document.viewModels().size(),
                document.compositeTypes().size(),
                document.enums().size()
        );

        return 0;
    }
}

package com.graphorm.cli.command;

import com.graphorm.schema.formatter.SchemaFormatter;
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
 * Canonical schema formatter for GraphORM (.graphorm) files.
 */
@Command(
        name = "fmt",
        aliases = {"format"},
        description = "Format a GraphORM schema file canonically",
        mixinStandardHelpOptions = true
)
public class FormatCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(
            names = {"-s", "--schema"},
            description = "Path to the GraphORM schema file (default: ${DEFAULT-VALUE})",
            defaultValue = "schema.graphorm"
    )
    private Path schemaPath = Path.of("schema.graphorm");

    @Option(
            names = {"-w", "--write"},
            description = "Write formatted output back to the schema file in-place (default: false)"
    )
    private boolean write = false;

    @Option(
            names = {"--check"},
            description = "Check if the schema file is formatted canonically without writing (exit 0 if formatted, 1 if unformatted)"
    )
    private boolean check = false;

    public FormatCommand() {}

    public FormatCommand(Path schemaPath, boolean write, boolean check) {
        this.schemaPath = schemaPath;
        this.write = write;
        this.check = check;
    }

    public void setSchemaPath(Path schemaPath) {
        this.schemaPath = schemaPath;
    }

    public void setWrite(boolean write) {
        this.write = write;
    }

    public void setCheck(boolean check) {
        this.check = check;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
        PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);

        if (!Files.exists(schemaPath)) {
            err.println("Error: Schema file does not exist: " + schemaPath);
            return 1;
        }

        String raw;
        try {
            raw = Files.readString(schemaPath, StandardCharsets.UTF_8);
        } catch (Exception e) {
            err.println("Error reading schema file: " + e.getMessage());
            return 1;
        }

        String formatted;
        try {
            formatted = SchemaFormatter.format(raw);
        } catch (Exception e) {
            err.println("Schema formatting failed: " + e.getMessage());
            return 1;
        }

        String normRaw = raw.replace("\r\n", "\n").stripTrailing();
        String normFmt = formatted.replace("\r\n", "\n").stripTrailing();
        boolean isAlreadyFormatted = normRaw.equals(normFmt);

        if (check) {
            if (isAlreadyFormatted) {
                out.println("Schema file is canonically formatted: " + schemaPath);
                return 0;
            } else {
                err.println("Schema file needs formatting: " + schemaPath);
                return 1;
            }
        }

        if (write) {
            if (isAlreadyFormatted) {
                out.println("Schema file is already formatted: " + schemaPath);
                return 0;
            }
            try {
                Files.writeString(schemaPath, formatted, StandardCharsets.UTF_8);
                out.println("Formatted " + schemaPath);
                return 0;
            } catch (Exception e) {
                err.println("Failed to write formatted schema file: " + e.getMessage());
                return 1;
            }
        }

        out.print(formatted);
        out.flush();
        return 0;
    }
}

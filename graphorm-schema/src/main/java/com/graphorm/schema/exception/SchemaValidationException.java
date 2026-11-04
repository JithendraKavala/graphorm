package com.graphorm.schema.exception;

import com.graphorm.core.exception.GraphOrmException;
import com.graphorm.schema.validation.Diagnostic;

import java.io.Serial;
import java.util.List;

/**
 * Thrown when schema syntax parsing or semantic validation encounters errors.
 */
public class SchemaValidationException extends GraphOrmException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<Diagnostic> diagnostics;
    private final String sourceCode;

    public SchemaValidationException(List<Diagnostic> diagnostics) {
        this(diagnostics, null);
    }

    public SchemaValidationException(List<Diagnostic> diagnostics, String sourceCode) {
        super(formatErrorMessage(diagnostics, sourceCode));
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        this.sourceCode = sourceCode;
    }

    public SchemaValidationException(String message, List<Diagnostic> diagnostics, String sourceCode) {
        super(message + "\n" + formatErrorMessage(diagnostics, sourceCode));
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        this.sourceCode = sourceCode;
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    public List<Diagnostic> getErrors() {
        return diagnostics.stream().filter(Diagnostic::isError).toList();
    }

    public String getSourceCode() {
        return sourceCode;
    }

    private static String formatErrorMessage(List<Diagnostic> diagnostics, String sourceCode) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return "Schema validation failed with unknown errors.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Schema validation failed with ")
                .append(diagnostics.size())
                .append(" issue(s):")
                .append(System.lineSeparator());

        for (Diagnostic d : diagnostics) {
            if (sourceCode != null && !sourceCode.isBlank()) {
                sb.append(d.formatWithCaret(sourceCode));
            } else {
                sb.append(d.format()).append(System.lineSeparator());
            }
        }
        return sb.toString().trim();
    }
}

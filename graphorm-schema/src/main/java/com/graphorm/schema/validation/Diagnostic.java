package com.graphorm.schema.validation;

import com.graphorm.schema.ast.SourcePosition;

import java.util.Objects;

/**
 * Diagnostic record representing a compiler warning or syntax/semantic error with source coordinates.
 *
 * @param severity   diagnostic severity (ERROR, WARNING, INFO)
 * @param message    diagnostic description
 * @param position   source coordinate location
 */
public record Diagnostic(
        Severity severity,
        String message,
        SourcePosition position
) {

    public Diagnostic {
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(message, "message must not be null");
        if (position == null) {
            position = SourcePosition.unknown();
        }
    }

    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    public static Diagnostic error(String message, SourcePosition position) {
        return new Diagnostic(Severity.ERROR, message, position);
    }

    public static Diagnostic error(String message, String file, int line, int col) {
        return new Diagnostic(Severity.ERROR, message, new SourcePosition(file, line, col, line, col + 1));
    }

    public static Diagnostic warning(String message, SourcePosition position) {
        return new Diagnostic(Severity.WARNING, message, position);
    }

    public static Diagnostic info(String message, SourcePosition position) {
        return new Diagnostic(Severity.INFO, message, position);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    /**
     * Formats the diagnostic as a concise single-line message.
     */
    public String format() {
        return String.format("[%s] %s:%d:%d - %s",
                severity,
                position.sourceFile(),
                position.line(),
                position.column(),
                message);
    }

    /**
     * Formats the diagnostic with a source code excerpt and a visual caret pointer (^).
     *
     * @param sourceCode full source code text
     * @return multiline formatted diagnostic with caret indicator
     */
    public String formatWithCaret(String sourceCode) {
        StringBuilder sb = new StringBuilder();
        sb.append(format()).append(System.lineSeparator());

        if (sourceCode != null && !sourceCode.isBlank() && position.line() > 0) {
            String[] lines = sourceCode.split("\\R");
            int lineIdx = position.line() - 1;
            if (lineIdx < lines.length) {
                String lineText = lines[lineIdx];
                sb.append(String.format(" %5d | %s", position.line(), lineText)).append(System.lineSeparator());
                int col = Math.max(1, position.column());
                sb.append("       | ");
                sb.append(" ".repeat(Math.max(0, col - 1)));
                sb.append("^");
                sb.append(System.lineSeparator());
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return format();
    }
}

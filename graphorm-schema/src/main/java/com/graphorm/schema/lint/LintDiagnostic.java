package com.graphorm.schema.lint;

import com.graphorm.schema.ast.SourcePosition;

import java.util.Objects;

/**
 * Diagnostic issue reported by the GraphORM schema linter.
 */
public record LintDiagnostic(
        SourcePosition position,
        String ruleName,
        LintSeverity severity,
        String message
) {

    public LintDiagnostic {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(ruleName, "ruleName must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    public static LintDiagnostic warning(String ruleName, String message, SourcePosition position) {
        return new LintDiagnostic(position, ruleName, LintSeverity.WARNING, message);
    }

    public static LintDiagnostic error(String ruleName, String message, SourcePosition position) {
        return new LintDiagnostic(position, ruleName, LintSeverity.ERROR, message);
    }

    public boolean isError() {
        return severity.isError();
    }

    public boolean isWarning() {
        return severity.isWarning();
    }

    public String formatWithCaret(String source) {
        StringBuilder sb = new StringBuilder();
        sb.append(severity == LintSeverity.ERROR ? "error" : "warning")
                .append(" [").append(ruleName).append("]: ")
                .append(message)
                .append(" at ").append(position.sourceFile())
                .append(":").append(position.line())
                .append(":").append(position.column())
                .append("\n");

        if (source != null && !source.isEmpty()) {
            String[] lines = source.split("\\r?\\n");
            int lineIdx = position.line() - 1;
            if (lineIdx >= 0 && lineIdx < lines.length) {
                String lineContent = lines[lineIdx];
                sb.append(String.format(" %4d | %s%n", position.line(), lineContent));
                sb.append("      | ");
                int col = Math.max(1, position.column());
                sb.append(" ".repeat(col - 1));
                sb.append("^\n");
            }
        }
        return sb.toString();
    }
}

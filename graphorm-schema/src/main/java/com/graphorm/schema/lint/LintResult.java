package com.graphorm.schema.lint;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of executing schema linting rules against a GraphORM schema.
 */
public record LintResult(
        List<LintDiagnostic> diagnostics
) {

    public LintResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static LintResult of(List<LintDiagnostic> diagnostics) {
        return new LintResult(diagnostics);
    }

    public static LintResult clean() {
        return new LintResult(List.of());
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(LintDiagnostic::isError);
    }

    public boolean hasWarnings() {
        return diagnostics.stream().anyMatch(LintDiagnostic::isWarning);
    }

    public boolean isClean() {
        return diagnostics.isEmpty();
    }

    public List<LintDiagnostic> getErrors() {
        return diagnostics.stream().filter(LintDiagnostic::isError).toList();
    }

    public List<LintDiagnostic> getWarnings() {
        return diagnostics.stream().filter(LintDiagnostic::isWarning).toList();
    }
}

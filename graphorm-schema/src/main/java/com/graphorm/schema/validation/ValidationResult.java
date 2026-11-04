package com.graphorm.schema.validation;

import com.graphorm.schema.exception.SchemaValidationException;

import java.util.List;
import java.util.Objects;

/**
 * Result of semantic schema validation containing diagnostics.
 */
public record ValidationResult(
        List<Diagnostic> diagnostics
) {

    public ValidationResult {
        Objects.requireNonNull(diagnostics, "diagnostics must not be null");
        diagnostics = List.copyOf(diagnostics);
    }

    public static ValidationResult valid() {
        return new ValidationResult(List.of());
    }

    public static ValidationResult of(List<Diagnostic> diagnostics) {
        return new ValidationResult(diagnostics);
    }

    public boolean isValid() {
        return diagnostics.stream().noneMatch(Diagnostic::isError);
    }

    public boolean hasErrors() {
        return !isValid();
    }

    public List<Diagnostic> getErrors() {
        return diagnostics.stream().filter(Diagnostic::isError).toList();
    }

    public List<Diagnostic> getWarnings() {
        return diagnostics.stream().filter(d -> d.severity() == Diagnostic.Severity.WARNING).toList();
    }

    public void throwIfInvalid() {
        throwIfInvalid(null);
    }

    public void throwIfInvalid(String sourceCode) {
        if (hasErrors()) {
            throw new SchemaValidationException(diagnostics, sourceCode);
        }
    }
}

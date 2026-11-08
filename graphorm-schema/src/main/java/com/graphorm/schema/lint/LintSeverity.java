package com.graphorm.schema.lint;

/**
 * Severity level of a schema lint diagnostic.
 */
public enum LintSeverity {
    WARNING,
    ERROR;

    public boolean isError() {
        return this == ERROR;
    }

    public boolean isWarning() {
        return this == WARNING;
    }
}

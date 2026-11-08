package com.graphorm.jdbc.migration;

import java.util.List;
import java.util.Objects;

/**
 * Result of executing database migrations.
 */
public record MigrationResult(
        List<String> appliedMigrationNames,
        long totalDurationMs,
        boolean success
) {

    public MigrationResult {
        appliedMigrationNames = appliedMigrationNames == null ? List.of() : List.copyOf(appliedMigrationNames);
    }
}

package com.graphorm.jdbc.migration;

import java.util.List;
import java.util.Objects;

/**
 * Detailed status report of applied, pending, and tampered database migrations.
 */
public record MigrationStatus(
        List<AppliedMigration> appliedMigrations,
        List<MigrationFile> pendingMigrations,
        List<MigrationTamper> tamperedMigrations
) {

    public MigrationStatus {
        appliedMigrations = appliedMigrations == null ? List.of() : List.copyOf(appliedMigrations);
        pendingMigrations = pendingMigrations == null ? List.of() : List.copyOf(pendingMigrations);
        tamperedMigrations = tamperedMigrations == null ? List.of() : List.copyOf(tamperedMigrations);
    }

    public boolean hasPending() {
        return !pendingMigrations.isEmpty();
    }

    public boolean hasTampered() {
        return !tamperedMigrations.isEmpty();
    }

    public boolean isUpToDate() {
        return !hasPending() && !hasTampered();
    }
}

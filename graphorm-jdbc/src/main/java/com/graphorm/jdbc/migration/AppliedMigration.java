package com.graphorm.jdbc.migration;

import java.time.Instant;
import java.util.Objects;

/**
 * Record representing an executed database migration recorded in the ledger.
 *
 * @param id              the auto-incremented migration sequence id
 * @param name            the migration filename / identifier
 * @param checksum        the SHA-256 checksum of the migration content
 * @param appliedAt       the timestamp when the migration was applied
 * @param executionTimeMs execution time in milliseconds
 */
public record AppliedMigration(
        int id,
        String name,
        String checksum,
        Instant appliedAt,
        long executionTimeMs
) {

    public AppliedMigration {
        Objects.requireNonNull(name, "Migration name must not be null");
        Objects.requireNonNull(checksum, "Migration checksum must not be null");
        Objects.requireNonNull(appliedAt, "appliedAt timestamp must not be null");
    }
}

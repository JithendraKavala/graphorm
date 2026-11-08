package com.graphorm.jdbc.migration;

import java.util.Objects;

/**
 * Record describing a detected checksum tamper where an applied migration was modified on disk.
 */
public record MigrationTamper(
        String migrationName,
        String appliedChecksum,
        String diskChecksum
) {

    public MigrationTamper {
        Objects.requireNonNull(migrationName, "Migration name must not be null");
        Objects.requireNonNull(appliedChecksum, "Applied checksum must not be null");
        Objects.requireNonNull(diskChecksum, "Disk checksum must not be null");
    }
}

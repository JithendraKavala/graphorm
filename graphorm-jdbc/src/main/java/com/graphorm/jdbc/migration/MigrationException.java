package com.graphorm.jdbc.migration;

import java.io.Serial;

/**
 * Thrown when a migration operation fails (checksum mismatch, lock timeout, SQL syntax error).
 */
public class MigrationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MigrationException(String message) {
        super(message);
    }

    public MigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}

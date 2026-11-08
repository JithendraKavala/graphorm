package com.graphorm.jdbc.migration;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Represents a migration script file stored on disk.
 *
 * @param path     the path to the SQL migration file
 * @param name     the migration identifier (e.g. "20261108120000_init" or "001_create_users")
 * @param content  the raw SQL content of the migration
 * @param checksum the SHA-256 checksum calculated from the content
 */
public record MigrationFile(
        Path path,
        String name,
        String content,
        String checksum
) {

    public MigrationFile {
        Objects.requireNonNull(path, "Migration path must not be null");
        Objects.requireNonNull(name, "Migration name must not be null");
        Objects.requireNonNull(content, "Migration content must not be null");
        Objects.requireNonNull(checksum, "Migration checksum must not be null");
    }

    /**
     * Reads a migration file from disk.
     */
    public static MigrationFile load(Path path, String name) throws IOException {
        String content = Files.readString(path);
        String checksum = ChecksumCalculator.calculateChecksum(content);
        return new MigrationFile(path, name, content, checksum);
    }

    /**
     * Discovers and loads all migration files in the specified directory, sorted lexicographically by name.
     * Supports both flat directories (e.g. `migrations/001_init.sql`) and subdirectories (e.g. `migrations/001_init/migration.sql`).
     *
     * @param migrationsDir the root directory containing migration files
     * @return sorted list of discovered migration files
     * @throws IOException if directory reading fails
     */
    public static List<MigrationFile> loadAll(Path migrationsDir) throws IOException {
        if (!Files.exists(migrationsDir)) {
            return List.of();
        }

        List<MigrationFile> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(migrationsDir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry) && entry.getFileName().toString().endsWith(".sql")) {
                    String fileName = entry.getFileName().toString();
                    String name = fileName.substring(0, fileName.lastIndexOf('.'));
                    files.add(load(entry, name));
                } else if (Files.isDirectory(entry)) {
                    Path nestedSql = entry.resolve("migration.sql");
                    if (Files.isRegularFile(nestedSql)) {
                        String name = entry.getFileName().toString();
                        files.add(load(nestedSql, name));
                    }
                }
            }
        }

        files.sort(Comparator.comparing(MigrationFile::name));
        return List.copyOf(files);
    }
}

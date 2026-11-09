package com.graphorm.spring.boot.migration;

import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.migration.MigrationFile;
import com.graphorm.jdbc.migration.MigrationManager;
import com.graphorm.jdbc.migration.MigrationResult;
import com.graphorm.spring.boot.autoconfigure.GraphOrmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Spring Boot initializer that automatically applies pending GraphORM schema migrations
 * on application startup using {@link MigrationManager} and the cryptographic ledger {@code _graphorm_migrations}.
 */
public class GraphOrmMigrationInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(GraphOrmMigrationInitializer.class);

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final GraphOrmProperties properties;
    private final ResourceLoader resourceLoader;

    public GraphOrmMigrationInitializer(
            DataSource dataSource,
            SqlDialect dialect,
            GraphOrmProperties properties,
            ResourceLoader resourceLoader
    ) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource must not be null");
        this.dialect = Objects.requireNonNull(dialect, "SqlDialect must not be null");
        this.properties = Objects.requireNonNull(properties, "GraphOrmProperties must not be null");
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "ResourceLoader must not be null");
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        migrate();
    }

    /**
     * Executes pending migrations against the configured DataSource.
     *
     * @return result of applied migrations
     * @throws SQLException if a database error occurs
     * @throws IOException  if reading migration files fails
     */
    public MigrationResult migrate() throws SQLException, IOException {
        if (!properties.getMigration().isEnabled()) {
            log.info("GraphORM schema migrations are disabled (graphorm.migration.enabled=false)");
            return new MigrationResult(List.of(), 0, true);
        }

        String location = properties.getMigration().getLocation();
        log.info("Checking GraphORM schema migrations at location: '{}'", location);

        List<MigrationFile> migrationFiles = loadMigrationFiles(location);
        if (migrationFiles.isEmpty()) {
            log.info("No GraphORM migration scripts discovered in '{}'; skipping migration execution", location);
            return new MigrationResult(List.of(), 0, true);
        }

        log.info("Discovered {} GraphORM migration file(s) to evaluate: {}",
                migrationFiles.size(), migrationFiles.stream().map(MigrationFile::name).toList());

        try (Connection conn = dataSource.getConnection()) {
            MigrationManager manager = new MigrationManager(dialect);
            MigrationResult result = manager.apply(migrationFiles, conn);
            if (!result.appliedMigrationNames().isEmpty()) {
                log.info("Successfully applied {} GraphORM migration(s) in {} ms: {}",
                        result.appliedMigrationNames().size(), result.totalDurationMs(), result.appliedMigrationNames());
            } else {
                log.info("GraphORM database schema is up-to-date; 0 pending migrations.");
            }
            return result;
        }
    }

    /**
     * Discovers and loads all migration files from classpath patterns or local filesystem paths.
     */
    public List<MigrationFile> loadMigrationFiles(String location) throws IOException {
        List<MigrationFile> files = new ArrayList<>();

        // 1. Check if location points to a local directory on the filesystem (avoid InvalidPathException on Windows)
        if (!location.startsWith("classpath:") && !location.startsWith("classpath*:")) {
            try {
                Path directPath = Path.of(location.replaceFirst("^file:", ""));
                if (Files.exists(directPath) && Files.isDirectory(directPath)) {
                    return MigrationFile.loadAll(directPath);
                }
            } catch (InvalidPathException e) {
                log.debug("Location '{}' is not a valid filesystem path: {}", location, e.getMessage());
            }
        }

        // 2. Discover via Spring Resource Pattern Resolver (using classpath*: to support fat JARs)
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(resourceLoader);
        String cleanLocation = location.endsWith("/") ? location.substring(0, location.length() - 1) : location;
        if (!cleanLocation.startsWith("classpath:") && !cleanLocation.startsWith("classpath*:")) {
            cleanLocation = "classpath*:" + cleanLocation;
        } else if (cleanLocation.startsWith("classpath:")) {
            cleanLocation = "classpath*:" + cleanLocation.substring("classpath:".length());
        }

        // Search both flat (*.sql) and nested (* /migration.sql)
        String[] patterns = new String[]{
                cleanLocation + "/*.sql",
                cleanLocation + "/**/migration.sql"
        };

        for (String pattern : patterns) {
            try {
                Resource[] resources = resolver.getResources(pattern);
                for (Resource resource : resources) {
                    if (resource.isReadable()) {
                        String name = extractMigrationName(resource);
                        try (InputStream in = resource.getInputStream()) {
                            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                            files.add(MigrationFile.of(name, content));
                        }
                    }
                }
            } catch (IOException e) {
                log.debug("Resource discovery for pattern '{}' returned 0 resources: {}", pattern, e.getMessage());
            }
        }

        files.sort(Comparator.comparing(MigrationFile::name));
        return List.copyOf(files);
    }

    private String extractMigrationName(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null) {
            return "migration_" + System.currentTimeMillis();
        }
        if ("migration.sql".equalsIgnoreCase(filename)) {
            try {
                String uriStr = resource.getURI().toString();
                String[] parts = uriStr.split("/");
                if (parts.length >= 2) {
                    return parts[parts.length - 2];
                }
            } catch (Exception ignored) {
            }
        }
        if (filename.toLowerCase().endsWith(".sql")) {
            return filename.substring(0, filename.length() - 4);
        }
        return filename;
    }
}

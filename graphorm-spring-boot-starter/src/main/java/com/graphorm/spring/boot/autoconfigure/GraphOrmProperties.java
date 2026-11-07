package com.graphorm.spring.boot.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for GraphORM Spring Boot Starter.
 */
@ConfigurationProperties(prefix = "graphorm")
public class GraphOrmProperties {

    /**
     * Location of the GraphORM schema DSL file (e.g. classpath:schema.graphorm).
     */
    private String schemaLocation = "classpath:schema.graphorm";

    /**
     * Alias for schema location (e.g. classpath:schema.graphorm).
     */
    private String schemaPath;

    /**
     * Target SQL dialect (e.g. "postgresql").
     */
    private String dialect = "postgresql";

    /**
     * Whether to log executed SQL queries and parameters to stdout/logger.
     */
    private boolean sqlLogging = false;

    /**
     * Default batch size for batched IN query loading.
     */
    private int batchSize = 100;

    /**
     * GraphQL specific auto-configuration properties.
     */
    private GraphqlProperties graphql = new GraphqlProperties();

    /**
     * Logging specific configuration properties.
     */
    private LoggingProperties logging = new LoggingProperties();

    public String getSchemaLocation() {
        return schemaLocation;
    }

    public void setSchemaLocation(String schemaLocation) {
        this.schemaLocation = schemaLocation;
    }

    public String getSchemaPath() {
        return schemaPath;
    }

    public void setSchemaPath(String schemaPath) {
        this.schemaPath = schemaPath;
    }

    public String resolveSchemaLocation() {
        if (schemaPath != null && !schemaPath.isBlank()) {
            return schemaPath;
        }
        return schemaLocation;
    }

    public String getDialect() {
        return dialect;
    }

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }

    public boolean isSqlLogging() {
        return sqlLogging;
    }

    public void setSqlLogging(boolean sqlLogging) {
        this.sqlLogging = sqlLogging;
    }

    public boolean isShowSqlEnabled() {
        return sqlLogging || (logging != null && logging.isShowSql());
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public GraphqlProperties getGraphql() {
        return graphql;
    }

    public void setGraphql(GraphqlProperties graphql) {
        this.graphql = graphql;
    }

    public LoggingProperties getLogging() {
        return logging;
    }

    public void setLogging(LoggingProperties logging) {
        this.logging = logging;
    }

    public static class GraphqlProperties {
        private boolean enabled = true;
        private boolean autoFetchers = true;
        private int batchSize = 100;
        private boolean failFastOnViewMutation = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAutoFetchers() {
            return autoFetchers;
        }

        public void setAutoFetchers(boolean autoFetchers) {
            this.autoFetchers = autoFetchers;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public boolean isFailFastOnViewMutation() {
            return failFastOnViewMutation;
        }

        public void setFailFastOnViewMutation(boolean failFastOnViewMutation) {
            this.failFastOnViewMutation = failFastOnViewMutation;
        }
    }

    public static class LoggingProperties {
        private boolean showSql = false;

        public boolean isShowSql() {
            return showSql;
        }

        public void setShowSql(boolean showSql) {
            this.showSql = showSql;
        }
    }
}

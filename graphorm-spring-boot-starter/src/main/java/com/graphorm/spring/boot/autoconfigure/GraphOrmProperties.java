package com.graphorm.spring.boot.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

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
    private String dialect = "auto";

    /**
     * Optional bean name of the DataSource to bind to. If not specified, the primary or unique DataSource bean is used.
     */
    private String datasourceBeanName;

    /**
     * Optional bean name of the PlatformTransactionManager to bind to. If not specified, the primary or unique transaction manager bean is used.
     */
    private String transactionManagerBeanName;

    /**
     * Whether to log executed SQL queries and parameters to stdout/logger.
     */
    private boolean sqlLogging = false;

    /**
     * Default batch size for batched IN query loading.
     */
    private int batchSize = 100;

    /**
     * Whether to enable Project Loom virtual threads for async executor operations (e.g. DataLoader batch hydration).
     */
    private boolean virtualThreads = false;

    /**
     * GraphQL specific auto-configuration properties.
     */
    @NestedConfigurationProperty
    private GraphqlProperties graphql = new GraphqlProperties();

    /**
     * Logging specific configuration properties.
     */
    @NestedConfigurationProperty
    private LoggingProperties logging = new LoggingProperties();

    /**
     * Security specific configuration properties (TASK-15.1, TASK-15.2).
     */
    @NestedConfigurationProperty
    private SecurityProperties security = new SecurityProperties();

    /**
     * Metrics specific configuration properties (TASK-15.4).
     */
    @NestedConfigurationProperty
    private MetricsProperties metrics = new MetricsProperties();

    /**
     * Multi-tenancy configuration properties (Phase 22).
     */
    @NestedConfigurationProperty
    private TenantProperties tenant = new TenantProperties();

    /**
     * Multi-tier caching configuration properties (Phase 23).
     */
    @NestedConfigurationProperty
    private CacheProperties cache = new CacheProperties();

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

    public String getDatasourceBeanName() {
        return datasourceBeanName;
    }

    public void setDatasourceBeanName(String datasourceBeanName) {
        this.datasourceBeanName = datasourceBeanName;
    }

    public String getTransactionManagerBeanName() {
        return transactionManagerBeanName;
    }

    public void setTransactionManagerBeanName(String transactionManagerBeanName) {
        this.transactionManagerBeanName = transactionManagerBeanName;
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

    public boolean isVirtualThreads() {
        return virtualThreads;
    }

    public void setVirtualThreads(boolean virtualThreads) {
        this.virtualThreads = virtualThreads;
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

    public SecurityProperties getSecurity() {
        return security;
    }

    public void setSecurity(SecurityProperties security) {
        this.security = security;
    }

    public MetricsProperties getMetrics() {
        return metrics;
    }

    public void setMetrics(MetricsProperties metrics) {
        this.metrics = metrics;
    }

    public TenantProperties getTenant() {
        return tenant;
    }

    public void setTenant(TenantProperties tenant) {
        this.tenant = tenant;
    }

    public CacheProperties getCache() {
        return cache;
    }

    public void setCache(CacheProperties cache) {
        this.cache = cache;
    }

    public static class GraphqlProperties {
        private boolean enabled = true;
        private boolean autoFetchers = true;
        private int batchSize = 100;
        private boolean failFastOnViewMutation = true;
        private boolean virtualThreads = false;

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

        public boolean isVirtualThreads() {
            return virtualThreads;
        }

        public void setVirtualThreads(boolean virtualThreads) {
            this.virtualThreads = virtualThreads;
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

    public static class SecurityProperties {
        private boolean depthLimitEnabled = true;
        private int maxQueryDepth = 15;
        private boolean complexityLimitEnabled = true;
        private int maxQueryComplexity = 200;
        private boolean fieldAuthorizationEnabled = true;

        public boolean isDepthLimitEnabled() {
            return depthLimitEnabled;
        }

        public void setDepthLimitEnabled(boolean depthLimitEnabled) {
            this.depthLimitEnabled = depthLimitEnabled;
        }

        public int getMaxQueryDepth() {
            return maxQueryDepth;
        }

        public void setMaxQueryDepth(int maxQueryDepth) {
            this.maxQueryDepth = maxQueryDepth;
        }

        public boolean isComplexityLimitEnabled() {
            return complexityLimitEnabled;
        }

        public void setComplexityLimitEnabled(boolean complexityLimitEnabled) {
            this.complexityLimitEnabled = complexityLimitEnabled;
        }

        public int getMaxQueryComplexity() {
            return maxQueryComplexity;
        }

        public void setMaxQueryComplexity(int maxQueryComplexity) {
            this.maxQueryComplexity = maxQueryComplexity;
        }

        public boolean isFieldAuthorizationEnabled() {
            return fieldAuthorizationEnabled;
        }

        public void setFieldAuthorizationEnabled(boolean fieldAuthorizationEnabled) {
            this.fieldAuthorizationEnabled = fieldAuthorizationEnabled;
        }
    }

    public static class MetricsProperties {
        private boolean enabled = true;
        private boolean queryDurationEnabled = true;
        private boolean dataloaderBatchSizeEnabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isQueryDurationEnabled() {
            return queryDurationEnabled;
        }

        public void setQueryDurationEnabled(boolean queryDurationEnabled) {
            this.queryDurationEnabled = queryDurationEnabled;
        }

        public boolean isDataloaderBatchSizeEnabled() {
            return dataloaderBatchSizeEnabled;
        }

        public void setDataloaderBatchSizeEnabled(boolean dataloaderBatchSizeEnabled) {
            this.dataloaderBatchSizeEnabled = dataloaderBatchSizeEnabled;
        }
    }

    public static class TenantProperties {
        private boolean enabled = true;
        private String headerName = "X-Tenant-ID";
        private String jwtClaim = "tenant_id";
        private boolean allowBypass = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public String getJwtClaim() {
            return jwtClaim;
        }

        public void setJwtClaim(String jwtClaim) {
            this.jwtClaim = jwtClaim;
        }

        public boolean isAllowBypass() {
            return allowBypass;
        }

        public void setAllowBypass(boolean allowBypass) {
            this.allowBypass = allowBypass;
        }
    }

    public static class CacheProperties {
        private boolean enabled = true;
        private long l1PlanCacheSize = 1000;
        private String l2Type = "caffeine";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getL1PlanCacheSize() {
            return l1PlanCacheSize;
        }

        public void setL1PlanCacheSize(long l1PlanCacheSize) {
            this.l1PlanCacheSize = l1PlanCacheSize;
        }

        public String getL2Type() {
            return l2Type;
        }

        public void setL2Type(String l2Type) {
            this.l2Type = l2Type;
        }
    }
}

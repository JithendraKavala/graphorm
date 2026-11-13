package com.graphorm.spring.boot.autoconfigure;

import com.graphorm.core.cache.CaffeineGraphOrmCacheManager;
import com.graphorm.core.cache.GraphOrmCacheManager;
import com.graphorm.core.cache.NoOpGraphOrmCacheManager;
import com.graphorm.core.execution.QueryExecutionEngine;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.tenant.TenantContext;
import com.graphorm.core.tenant.TenantContextProvider;
import com.graphorm.graphql.binding.ArgumentBinder;
import com.graphorm.graphql.loader.DataLoaderMetricsListener;
import com.graphorm.graphql.security.FieldAuthorizer;
import com.graphorm.jdbc.cache.QueryPlanCache;
import com.graphorm.jdbc.client.GraphOrmClient;
import com.graphorm.jdbc.dialect.MySqlDialect;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.dialect.SqliteDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.migration.MigrationManager;
import com.graphorm.jdbc.transaction.TransactionManager;
import com.graphorm.jdbc.util.StatementInterceptor;
import com.graphorm.core.event.EntityEventBroker;
import com.graphorm.core.event.InMemoryEntityEventBroker;
import com.graphorm.jdbc.event.PostgresNotificationListener;
import com.graphorm.schema.transformer.SchemaToMetadataTransformer;
import com.graphorm.spring.boot.aot.GraphOrmRuntimeHintsRegistrar;
import com.graphorm.spring.boot.graphql.GraphOrmExceptionHandler;
import com.graphorm.spring.boot.graphql.GraphQlAutoConfigurationCustomizer;
import com.graphorm.spring.boot.metrics.MicrometerDataLoaderMetricsListener;
import com.graphorm.spring.boot.metrics.MicrometerStatementInterceptor;
import com.graphorm.spring.boot.migration.GraphOrmMigrationInitializer;
import com.graphorm.spring.boot.security.SpringSecurityFieldAuthorizer;
import com.graphorm.spring.boot.tenant.SpringTenantContextProvider;
import com.graphorm.spring.boot.tenant.TenantHeaderFilter;
import com.graphorm.spring.boot.transaction.SpringConnectionProvider;
import com.graphorm.spring.boot.transaction.SpringTransactionManagerAdapter;
import graphql.GraphQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.graphql.GraphQlAutoConfiguration;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spring Boot 3+ Auto-Configuration for GraphORM.
 * Automatically configures {@link ModelRegistry}, {@link SqlDialect}, {@link JdbcExecutor},
 * {@link TransactionManager}, {@link GraphOrmClient}, caching, security, metrics, migrations, and GraphQL customizers.
 */
@AutoConfiguration(after = {DataSourceAutoConfiguration.class, GraphQlAutoConfiguration.class})
@ConditionalOnClass(DataSource.class)
@EnableConfigurationProperties(GraphOrmProperties.class)
@ImportRuntimeHints(GraphOrmRuntimeHintsRegistrar.class)
public class GraphOrmAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GraphOrmAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SqlDialect sqlDialect(
            GraphOrmProperties properties,
            ObjectProvider<DataSource> dataSourceProvider,
            BeanFactory beanFactory
    ) {
        String dialectName = properties.getDialect();
        if (dialectName != null && !dialectName.isBlank() && !"auto".equalsIgnoreCase(dialectName)) {
            if ("postgresql".equalsIgnoreCase(dialectName) || "postgres".equalsIgnoreCase(dialectName)) {
                return new PostgreSqlDialect();
            }
            if ("mysql".equalsIgnoreCase(dialectName) || "mariadb".equalsIgnoreCase(dialectName)) {
                return new MySqlDialect();
            }
            if ("sqlite".equalsIgnoreCase(dialectName)) {
                return new SqliteDialect();
            }
            throw new IllegalArgumentException(
                    "Unsupported SQL dialect '" + dialectName + "'. Supported dialects: [postgresql, postgres, mysql, sqlite, auto]. " +
                    "To use a custom dialect, register a custom SqlDialect bean in the application context."
            );
        }

        DataSource ds = resolveDataSource(dataSourceProvider, properties, beanFactory);
        if (ds != null) {
            try (java.sql.Connection conn = ds.getConnection()) {
                String productName = conn.getMetaData().getDatabaseProductName();
                SqlDialect detected = SqlDialect.fromDatabaseProductName(productName);
                log.info("Auto-detected SQL dialect '{}' for database product '{}'", detected.getClass().getSimpleName(), productName);
                return detected;
            } catch (Exception e) {
                log.warn("Failed to auto-detect SQL dialect from DataSource metadata ({}); defaulting to PostgreSqlDialect", e.getMessage());
            }
        }
        return new PostgreSqlDialect();
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelRegistry modelRegistry(GraphOrmProperties properties, ResourceLoader resourceLoader) {
        String schemaPath = properties.resolveSchemaLocation();
        Resource resource = resourceLoader.getResource(schemaPath);

        if (!resource.exists()) {
            log.info("GraphORM schema file not found at '{}'; initializing empty ModelRegistry", schemaPath);
            return ModelRegistry.builder().build();
        }

        try (InputStream in = resource.getInputStream()) {
            String schemaContent = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            log.info("Loading GraphORM schema from '{}' ({} bytes)", schemaPath, schemaContent.length());
            return SchemaToMetadataTransformer.transform(schemaContent);
        } catch (IOException e) {
            log.error("Failed to read GraphORM schema from '{}': {}", schemaPath, e.getMessage(), e);
            throw new IllegalStateException("Failed to load GraphORM schema from " + schemaPath, e);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryPlanCache queryPlanCache(GraphOrmProperties properties) {
        if (!properties.getCache().isEnabled()) {
            return QueryPlanCache.create(0);
        }
        return QueryPlanCache.create(properties.getCache().getL1PlanCacheSize());
    }

    @Bean
    @ConditionalOnMissingBean
    public EntityEventBroker entityEventBroker() {
        return new InMemoryEntityEventBroker();
    }

    @Bean
    @ConditionalOnMissingBean
    public GraphOrmCacheManager graphOrmCacheManager(GraphOrmProperties properties) {
        if (!properties.getCache().isEnabled() || "none".equalsIgnoreCase(properties.getCache().getL2Type())) {
            return new NoOpGraphOrmCacheManager();
        }
        return new CaffeineGraphOrmCacheManager();
    }

    private static StatementInterceptor createLoggingInterceptor() {
        return new StatementInterceptor() {
            private static final Logger sqlLog = LoggerFactory.getLogger("com.graphorm.sql");

            @Override
            public void beforeExecute(String sql, List<Object> parameters) {
                sqlLog.info("SQL: {} | params: {}", sql, parameters);
            }

            @Override
            public void afterExecute(String sql, List<Object> parameters, long durationNanos) {
                double durationMs = durationNanos / 1_000_000.0;
                sqlLog.debug("SQL executed in {} ms", String.format("%.2f", durationMs));
            }
        };
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "graphorm", name = "sql-logging", havingValue = "true")
    static class SqlLoggingConfiguration {
        @Bean(name = "graphOrmLoggingStatementInterceptor")
        @ConditionalOnMissingBean(name = "graphOrmLoggingStatementInterceptor")
        public StatementInterceptor graphOrmLoggingStatementInterceptor() {
            return createLoggingInterceptor();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "graphorm.logging", name = "show-sql", havingValue = "true")
    static class ShowSqlConfiguration {
        @Bean(name = "graphOrmLoggingStatementInterceptor")
        @ConditionalOnMissingBean(name = "graphOrmLoggingStatementInterceptor")
        public StatementInterceptor graphOrmLoggingStatementInterceptor() {
            return createLoggingInterceptor();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(io.micrometer.core.instrument.MeterRegistry.class)
    @ConditionalOnBean(io.micrometer.core.instrument.MeterRegistry.class)
    @ConditionalOnProperty(prefix = "graphorm.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class MetricsConfiguration {

        @Bean(name = "micrometerStatementInterceptor")
        @ConditionalOnProperty(prefix = "graphorm.metrics", name = "query-duration-enabled", havingValue = "true", matchIfMissing = true)
        @ConditionalOnMissingBean(name = "micrometerStatementInterceptor")
        public StatementInterceptor micrometerStatementInterceptor(io.micrometer.core.instrument.MeterRegistry meterRegistry) {
            return new MicrometerStatementInterceptor(meterRegistry);
        }

        @Bean
        @ConditionalOnProperty(prefix = "graphorm.metrics", name = "dataloader-batch-size-enabled", havingValue = "true", matchIfMissing = true)
        @ConditionalOnMissingBean(DataLoaderMetricsListener.class)
        public DataLoaderMetricsListener micrometerDataLoaderMetricsListener(io.micrometer.core.instrument.MeterRegistry meterRegistry) {
            return new MicrometerDataLoaderMetricsListener(meterRegistry);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MigrationManager.class)
    @ConditionalOnProperty(prefix = "graphorm.migration", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class MigrationConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public GraphOrmMigrationInitializer graphOrmMigrationInitializer(
                ObjectProvider<DataSource> dataSourceProvider,
                GraphOrmProperties properties,
                BeanFactory beanFactory,
                SqlDialect sqlDialect,
                ResourceLoader resourceLoader
        ) {
            DataSource ds = resolveDataSource(dataSourceProvider, properties, beanFactory);
            if (ds == null) {
                return null;
            }
            return new GraphOrmMigrationInitializer(ds, sqlDialect, properties, resourceLoader);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(PostgresNotificationListener.class)
    @ConditionalOnProperty(prefix = "graphorm.subscriptions", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class SubscriptionsConfiguration {

        @Bean(initMethod = "start", destroyMethod = "stop")
        @ConditionalOnMissingBean(PostgresNotificationListener.class)
        public PostgresNotificationListener postgresNotificationListener(
                ObjectProvider<DataSource> dataSourceProvider,
                GraphOrmProperties properties,
                BeanFactory beanFactory,
                SqlDialect sqlDialect,
                EntityEventBroker eventBroker
        ) {
            if (!(sqlDialect instanceof PostgreSqlDialect) || !PostgresNotificationListener.isDriverAvailable()) {
                return null;
            }
            DataSource ds = resolveDataSource(dataSourceProvider, properties, beanFactory);
            if (ds == null) {
                return null;
            }
            return new PostgresNotificationListener(
                    ds,
                    properties.getSubscriptions().getChannelName(),
                    eventBroker
            );
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "graphorm.tenant", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class MultiTenancyConfiguration {

        @Bean
        @ConditionalOnMissingBean(TenantContextProvider.class)
        public TenantContextProvider tenantContextProvider(
                GraphOrmProperties properties,
                ObjectProvider<com.graphorm.spring.boot.tenant.TenantAuthorizationStrategy> authorizationStrategyProvider
        ) {
            SpringTenantContextProvider provider = new SpringTenantContextProvider(
                    properties.getTenant(),
                    authorizationStrategyProvider.getIfAvailable()
            );
            TenantContext.setProvider(provider);
            return provider;
        }

        @Bean
        @ConditionalOnClass(name = "jakarta.servlet.Filter")
        @ConditionalOnProperty(prefix = "graphorm.tenant", name = "header-enabled", havingValue = "true", matchIfMissing = false)
        @ConditionalOnMissingBean(TenantHeaderFilter.class)
        public TenantHeaderFilter tenantHeaderFilter(
                GraphOrmProperties properties,
                ObjectProvider<com.graphorm.spring.boot.tenant.TenantAuthorizationStrategy> authorizationStrategyProvider
        ) {
            return new TenantHeaderFilter(properties.getTenant(), authorizationStrategyProvider.getIfAvailable());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(org.springframework.security.core.context.SecurityContextHolder.class)
    @ConditionalOnProperty(prefix = "graphorm.security", name = "field-authorization-enabled", havingValue = "true", matchIfMissing = true)
    static class SpringSecurityConfiguration {

        @Bean
        @ConditionalOnMissingBean(FieldAuthorizer.class)
        public FieldAuthorizer springSecurityFieldAuthorizer() {
            return new SpringSecurityFieldAuthorizer();
        }
    }

    @Bean
    @ConditionalOnClass(DataFetcherExceptionResolverAdapter.class)
    @ConditionalOnMissingBean
    public GraphOrmExceptionHandler graphOrmExceptionHandler() {
        return new GraphOrmExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcExecutor.ConnectionProvider connectionProvider() {
        return new SpringConnectionProvider();
    }

    private static DataSource resolveDataSource(
            ObjectProvider<DataSource> dataSourceProvider,
            GraphOrmProperties properties,
            BeanFactory beanFactory
    ) {
        String customBeanName = properties.getDatasourceBeanName();
        if (customBeanName != null && !customBeanName.isBlank()) {
            return beanFactory.getBean(customBeanName, DataSource.class);
        }
        DataSource ds = dataSourceProvider.getIfUnique();
        if (ds != null) {
            return ds;
        }
        return dataSourceProvider.getIfAvailable();
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcExecutor jdbcExecutor(
            ObjectProvider<DataSource> dataSourceProvider,
            GraphOrmProperties properties,
            BeanFactory beanFactory,
            SqlDialect sqlDialect,
            JdbcExecutor.ConnectionProvider connectionProvider,
            ObjectProvider<StatementInterceptor> interceptorsProvider,
            ObjectProvider<QueryPlanCache> planCacheProvider,
            ObjectProvider<GraphOrmCacheManager> cacheManagerProvider,
            ObjectProvider<EntityEventBroker> eventBrokerProvider,
            ObjectProvider<ModelRegistry> modelRegistryProvider,
            ObjectProvider<GraphOrmMigrationInitializer> migrationInitializerProvider
    ) {
        // Trigger migration initialization if configured
        migrationInitializerProvider.ifAvailable(init -> {});

        DataSource dataSource = resolveDataSource(dataSourceProvider, properties, beanFactory);
        if (dataSource == null) {
            throw new IllegalStateException(
                    "No DataSource bean found in application context. Ensure a DataSource bean is configured or configure 'graphorm.datasource-bean-name'."
            );
        }
        DataSource txAwareDataSource = dataSource instanceof TransactionAwareDataSourceProxy
                ? dataSource
                : new TransactionAwareDataSourceProxy(dataSource);

        List<StatementInterceptor> interceptors = interceptorsProvider.orderedStream().toList();
        QueryPlanCache planCache = planCacheProvider.getIfAvailable(() -> QueryPlanCache.getDefault());
        GraphOrmCacheManager cacheManager = cacheManagerProvider.getIfAvailable();

        JdbcExecutor executor = new JdbcExecutor(
                txAwareDataSource,
                sqlDialect,
                connectionProvider,
                planCache,
                cacheManager,
                interceptors.toArray(StatementInterceptor[]::new)
        );
        EntityEventBroker broker = eventBrokerProvider.getIfAvailable();
        if (broker != null) {
            executor.setEventBroker(broker);
        }
        ModelRegistry registry = modelRegistryProvider.getIfAvailable();
        if (registry != null) {
            executor.setModelRegistry(registry);
        }
        if (properties.getSubscriptions().isEnabled()) {
            executor.setNotifyChannel(properties.getSubscriptions().getChannelName());
            if (sqlDialect instanceof PostgreSqlDialect && PostgresNotificationListener.isDriverAvailable()) {
                executor.setNativePostgresNotify(true);
            }
        }
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean(QueryExecutionEngine.class)
    public QueryExecutionEngine queryExecutionEngine(JdbcExecutor jdbcExecutor) {
        return jdbcExecutor;
    }

    @Bean
    @ConditionalOnMissingBean(TransactionManager.class)
    public TransactionManager graphOrmTransactionManager(
            ObjectProvider<DataSource> dataSourceProvider,
            ObjectProvider<PlatformTransactionManager> txManagerProvider,
            GraphOrmProperties properties,
            BeanFactory beanFactory
    ) {
        DataSource dataSource = resolveDataSource(dataSourceProvider, properties, beanFactory);
        PlatformTransactionManager platformTxManager = null;
        String customTxName = properties.getTransactionManagerBeanName();
        if (customTxName != null && !customTxName.isBlank()) {
            platformTxManager = beanFactory.getBean(customTxName, PlatformTransactionManager.class);
        } else {
            platformTxManager = txManagerProvider.getIfAvailable();
        }
        return new SpringTransactionManagerAdapter(dataSource, platformTxManager);
    }

    @Bean
    @ConditionalOnMissingBean(GraphOrmClient.class)
    public GraphOrmClient graphOrmClient(
            JdbcExecutor jdbcExecutor,
            ModelRegistry modelRegistry,
            SqlDialect sqlDialect,
            TransactionManager transactionManager
    ) {
        return new GraphOrmClient(jdbcExecutor, modelRegistry, sqlDialect, transactionManager);
    }

    /**
     * Nested configuration for Spring for GraphQL auto-wiring.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({GraphQL.class, GraphQlSource.class, GraphQlSourceBuilderCustomizer.class})
    @ConditionalOnProperty(prefix = "graphorm.graphql", name = "enabled", havingValue = "true", matchIfMissing = true)
    public static class GraphOrmGraphQlConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public GraphQlAutoConfigurationCustomizer graphOrmGraphQlSourceBuilderCustomizer(
                ModelRegistry modelRegistry,
                JdbcExecutor jdbcExecutor,
                SqlDialect sqlDialect,
                GraphOrmProperties properties,
                ObjectProvider<FieldAuthorizer> fieldAuthorizerProvider,
                ObjectProvider<DataLoaderMetricsListener> metricsListenerProvider
        ) {
            FieldAuthorizer fieldAuthorizer = fieldAuthorizerProvider.getIfAvailable();
            if (GraphQlAutoConfigurationCustomizer.hasAuthDirectives(modelRegistry) &&
                    (fieldAuthorizer == null || fieldAuthorizer == FieldAuthorizer.PERMIT_ALL)) {
                throw new IllegalStateException(
                        "GraphORM schema defines @auth directives, but no non-default FieldAuthorizer bean is configured. " +
                        "Configure a custom FieldAuthorizer bean or include spring-boot-starter-security to prevent unauthorized access."
                );
            }
            return new GraphQlAutoConfigurationCustomizer(
                    modelRegistry,
                    jdbcExecutor,
                    sqlDialect,
                    properties,
                    new ArgumentBinder(),
                    fieldAuthorizer,
                    metricsListenerProvider.getIfAvailable()
            );
        }
    }
}

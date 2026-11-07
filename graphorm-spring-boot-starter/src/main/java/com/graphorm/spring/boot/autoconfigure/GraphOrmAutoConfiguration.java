package com.graphorm.spring.boot.autoconfigure;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.graphql.binding.ArgumentBinder;
import com.graphorm.graphql.loader.DataLoaderMetricsListener;
import com.graphorm.graphql.security.FieldAuthorizer;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.client.GraphOrmClient;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.transaction.TransactionManager;
import com.graphorm.jdbc.util.StatementInterceptor;
import com.graphorm.schema.transformer.SchemaToMetadataTransformer;
import com.graphorm.spring.boot.graphql.GraphOrmExceptionHandler;
import com.graphorm.spring.boot.graphql.GraphQlAutoConfigurationCustomizer;
import com.graphorm.spring.boot.metrics.MicrometerDataLoaderMetricsListener;
import com.graphorm.spring.boot.metrics.MicrometerStatementInterceptor;
import com.graphorm.spring.boot.security.SpringSecurityFieldAuthorizer;
import com.graphorm.spring.boot.transaction.SpringConnectionProvider;
import com.graphorm.spring.boot.transaction.SpringTransactionManagerAdapter;
import graphql.GraphQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * {@link TransactionManager}, {@link GraphOrmClient}, security, metrics, and GraphQL customizers.
 */
@AutoConfiguration(after = {DataSourceAutoConfiguration.class, GraphQlAutoConfiguration.class})
@ConditionalOnClass(DataSource.class)
@EnableConfigurationProperties(GraphOrmProperties.class)
public class GraphOrmAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GraphOrmAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SqlDialect sqlDialect(GraphOrmProperties properties) {
        String dialectName = properties.getDialect();
        if ("postgresql".equalsIgnoreCase(dialectName) || "postgres".equalsIgnoreCase(dialectName)) {
            return new PostgreSqlDialect();
        }
        log.warn("Unknown dialect '{}'; defaulting to PostgreSqlDialect", dialectName);
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

    @Bean
    @ConditionalOnMissingBean
    public JdbcExecutor jdbcExecutor(
            DataSource dataSource,
            SqlDialect sqlDialect,
            JdbcExecutor.ConnectionProvider connectionProvider,
            ObjectProvider<StatementInterceptor> interceptorsProvider
    ) {
        DataSource txAwareDataSource = dataSource instanceof TransactionAwareDataSourceProxy
                ? dataSource
                : new TransactionAwareDataSourceProxy(dataSource);

        List<StatementInterceptor> interceptors = interceptorsProvider.orderedStream().toList();
        return new JdbcExecutor(
                txAwareDataSource,
                sqlDialect,
                connectionProvider,
                interceptors.toArray(StatementInterceptor[]::new)
        );
    }

    @Bean
    @ConditionalOnMissingBean(TransactionManager.class)
    public TransactionManager graphOrmTransactionManager(
            DataSource dataSource,
            ObjectProvider<PlatformTransactionManager> txManagerProvider
    ) {
        return new SpringTransactionManagerAdapter(dataSource, txManagerProvider.getIfAvailable());
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
            return new GraphQlAutoConfigurationCustomizer(
                    modelRegistry,
                    jdbcExecutor,
                    sqlDialect,
                    properties,
                    new ArgumentBinder(),
                    fieldAuthorizerProvider.getIfAvailable(),
                    metricsListenerProvider.getIfAvailable()
            );
        }
    }
}

package com.graphorm.spring.boot.autoconfigure;

import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.graphql.security.FieldAuthorizer;
import com.graphorm.jdbc.client.GraphOrmClient;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.transaction.TransactionManager;
import com.graphorm.jdbc.util.StatementInterceptor;
import com.graphorm.spring.boot.graphql.GraphQlAutoConfigurationCustomizer;
import com.graphorm.spring.boot.transaction.SpringTransactionManagerAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.graphql.GraphQlAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import com.graphorm.core.cache.CaffeineGraphOrmCacheManager;
import com.graphorm.core.cache.GraphOrmCacheManager;
import com.graphorm.core.cache.NoOpGraphOrmCacheManager;
import com.graphorm.jdbc.cache.QueryPlanCache;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GraphOrmAutoConfiguration ApplicationContextRunner Tests (TASK-13.3, TASK-13.5, P3 Hardening)")
class GraphOrmAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    DataSourceTransactionManagerAutoConfiguration.class,
                    GraphOrmAutoConfiguration.class
            ))
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:graphorm_test;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver"
            );

    @Test
    @DisplayName("Default configuration registers all core GraphORM beans")
    void testDefaultConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GraphOrmProperties.class);
            assertThat(context).hasSingleBean(SqlDialect.class);
            assertThat(context).hasSingleBean(ModelRegistry.class);
            assertThat(context).hasSingleBean(JdbcExecutor.class);
            assertThat(context).hasSingleBean(TransactionManager.class);
            assertThat(context).hasSingleBean(GraphOrmClient.class);
            assertThat(context).hasSingleBean(com.graphorm.core.event.EntityEventBroker.class);

            ModelRegistry registry = context.getBean(ModelRegistry.class);
            assertThat(registry.findModel("Student")).isPresent();
            assertThat(registry.findModel("Department")).isPresent();
            assertThat(registry.findViewModel("StudentDashboard")).isPresent();

            TransactionManager txManager = context.getBean(TransactionManager.class);
            assertThat(txManager).isInstanceOf(SpringTransactionManagerAdapter.class);

            SqlDialect dialect = context.getBean(SqlDialect.class);
            assertThat(dialect).isInstanceOf(PostgreSqlDialect.class);
        });
    }

    @Test
    @DisplayName("Graceful fallback with informative error when no DataSource bean is configured")
    void testInformativeErrorWithoutDataSource() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(GraphOrmAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("No DataSource bean found in application context");
                });
    }

    @Test
    @DisplayName("Custom named DataSource binding via graphorm.datasource-bean-name")
    void testCustomNamedDataSourceBinding() {
        contextRunner
                .withUserConfiguration(MultipleDataSourcesConfig.class)
                .withPropertyValues("graphorm.datasource-bean-name=secondaryDataSource")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JdbcExecutor.class);
                    JdbcExecutor executor = context.getBean(JdbcExecutor.class);
                    assertThat(executor.dataSource()).isNotNull();
                });
    }

    @Test
    @DisplayName("Custom named PlatformTransactionManager binding via graphorm.transaction-manager-bean-name")
    void testCustomNamedTransactionManagerBinding() {
        contextRunner
                .withUserConfiguration(MultipleDataSourcesConfig.class)
                .withPropertyValues(
                        "graphorm.datasource-bean-name=secondaryDataSource",
                        "graphorm.transaction-manager-bean-name=secondaryTxManager"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TransactionManager.class);
                });
    }

    @Test
    @DisplayName("Custom schema location property loads target schema")
    void testCustomSchemaLocation() {
        contextRunner
                .withPropertyValues("graphorm.schema-location=classpath:test-schema.graphorm")
                .run(context -> {
                    ModelRegistry registry = context.getBean(ModelRegistry.class);
                    assertThat(registry.findModel("CustomEntity")).isPresent();
                    assertThat(registry.findModel("Student")).isNotPresent();
                });
    }

    @Test
    @DisplayName("SQL logging property registers StatementInterceptor bean")
    void testSqlLoggingProperty() {
        contextRunner
                .withPropertyValues("graphorm.sql-logging=true")
                .run(context -> {
                    assertThat(context).hasBean("graphOrmLoggingStatementInterceptor");
                    StatementInterceptor interceptor = context.getBean("graphOrmLoggingStatementInterceptor", StatementInterceptor.class);
                    assertThat(interceptor).isNotNull();
                });
    }

    @Test
    @DisplayName("graphorm.logging.show-sql=true also activates logging interceptor")
    void testShowSqlProperty() {
        contextRunner
                .withPropertyValues("graphorm.logging.show-sql=true")
                .run(context -> {
                    assertThat(context).hasBean("graphOrmLoggingStatementInterceptor");
                });
    }

    @Test
    @DisplayName("graphorm.graphql.enabled=false disables GraphQL customizer")
    void testGraphQlDisabled() {
        contextRunner
                .withPropertyValues("graphorm.graphql.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(GraphQlAutoConfigurationCustomizer.class);
                });
    }

    @Test
    @DisplayName("[DB-01] Unsupported SQL dialect throws IllegalArgumentException during context startup")
    void testUnsupportedDialectFailsFast() {
        contextRunner
                .withPropertyValues("graphorm.dialect=oracle")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("Unsupported SQL dialect 'oracle'");
                });
    }

    @Test
    @DisplayName("[DB-02] Configured MySQL dialect registers MySqlDialect bean")
    void testMySqlDialectConfigured() {
        contextRunner
                .withPropertyValues("graphorm.dialect=mysql")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SqlDialect.class);
                    assertThat(context.getBean(SqlDialect.class)).isInstanceOf(com.graphorm.jdbc.dialect.MySqlDialect.class);
                });
    }

    @Test
    @DisplayName("[DB-03] Configured SQLite dialect registers SqliteDialect bean")
    void testSqliteDialectConfigured() {
        contextRunner
                .withPropertyValues("graphorm.dialect=sqlite")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SqlDialect.class);
                    assertThat(context.getBean(SqlDialect.class)).isInstanceOf(com.graphorm.jdbc.dialect.SqliteDialect.class);
                });
    }

    @Test
    @DisplayName("[DB-04] Virtual threads configuration enables virtual threads on properties")
    void testVirtualThreadsConfiguration() {
        contextRunner
                .withPropertyValues("graphorm.virtual-threads=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    GraphOrmProperties props = context.getBean(GraphOrmProperties.class);
                    assertThat(props.isVirtualThreads()).isTrue();
                });
    }

    @Test
    @DisplayName("[SEC-01] @auth directives in schema without FieldAuthorizer bean fails fast at startup")
    void testAuthDirectiveWithoutAuthorizerFailsFast() {
        contextRunner
                .withConfiguration(AutoConfigurations.of(GraphQlAutoConfiguration.class))
                .withPropertyValues(
                        "graphorm.schema-location=classpath:security-schema.graphorm",
                        "graphorm.security.field-authorization-enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("@auth");
                });
    }

    @Test
    @DisplayName("[SEC-01] @auth directives in schema with configured FieldAuthorizer succeeds")
    void testAuthDirectiveWithCustomAuthorizerSucceeds() {
        contextRunner
                .withConfiguration(AutoConfigurations.of(GraphQlAutoConfiguration.class))
                .withPropertyValues("graphorm.schema-location=classpath:security-schema.graphorm")
                .withUserConfiguration(CustomAuthorizerConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FieldAuthorizer.class);
                });
    }

    @Test
    @DisplayName("ConditionalOnMissingBean allows custom user beans to override defaults")
    void testCustomBeanOverrides() {
        contextRunner
                .withUserConfiguration(CustomUserConfig.class)
                .run(context -> {
                    ModelRegistry registry = context.getBean(ModelRegistry.class);
                    assertThat(registry.findModel("UserOverrideModel")).isPresent();

                    SqlDialect dialect = context.getBean(SqlDialect.class);
                    assertThat(dialect).isSameAs(CustomUserConfig.CUSTOM_DIALECT);

                    GraphOrmClient client = context.getBean(GraphOrmClient.class);
                    assertThat(client).isSameAs(CustomUserConfig.CUSTOM_CLIENT);
                });
    }

    @Test
    @DisplayName("Default configuration registers QueryPlanCache and CaffeineGraphOrmCacheManager")
    void testDefaultCacheBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(QueryPlanCache.class);
            assertThat(context).hasSingleBean(GraphOrmCacheManager.class);
            GraphOrmCacheManager cacheManager = context.getBean(GraphOrmCacheManager.class);
            assertThat(cacheManager).isInstanceOf(CaffeineGraphOrmCacheManager.class);
        });
    }

    @Test
    @DisplayName("Cache disabled registers NoOpGraphOrmCacheManager")
    void testCacheDisabledRegistersNoOp() {
        contextRunner
                .withPropertyValues("graphorm.cache.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(GraphOrmCacheManager.class);
                    GraphOrmCacheManager cacheManager = context.getBean(GraphOrmCacheManager.class);
                    assertThat(cacheManager).isInstanceOf(NoOpGraphOrmCacheManager.class);
                });
    }

    @Test
    @DisplayName("L2 cache type none registers NoOpGraphOrmCacheManager")
    void testCacheL2TypeNoneRegistersNoOp() {
        contextRunner
                .withPropertyValues("graphorm.cache.l2-type=none")
                .run(context -> {
                    assertThat(context).hasSingleBean(GraphOrmCacheManager.class);
                    GraphOrmCacheManager cacheManager = context.getBean(GraphOrmCacheManager.class);
                    assertThat(cacheManager).isInstanceOf(NoOpGraphOrmCacheManager.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class MultipleDataSourcesConfig {
        @Bean
        @Primary
        public DataSource primaryDataSource() {
            org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
            ds.setURL("jdbc:h2:mem:primary;DB_CLOSE_DELAY=-1");
            return ds;
        }

        @Bean
        public DataSource secondaryDataSource() {
            org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
            ds.setURL("jdbc:h2:mem:secondary;DB_CLOSE_DELAY=-1");
            return ds;
        }

        @Bean
        public PlatformTransactionManager secondaryTxManager(DataSource secondaryDataSource) {
            return new DataSourceTransactionManager(secondaryDataSource);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomAuthorizerConfig {
        @Bean
        public FieldAuthorizer customFieldAuthorizer() {
            return roles -> true;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomUserConfig {
        static final SqlDialect CUSTOM_DIALECT = new PostgreSqlDialect();
        static final GraphOrmClient CUSTOM_CLIENT = GraphOrmClient.builder()
                .registry(ModelRegistry.builder().addModel(ModelMetadata.builder("UserOverrideModel").build()).build())
                .executor(new JdbcExecutor(new org.h2.jdbcx.JdbcDataSource()))
                .build();

        @Bean
        public SqlDialect sqlDialect() {
            return CUSTOM_DIALECT;
        }

        @Bean
        public ModelRegistry modelRegistry() {
            return ModelRegistry.builder()
                    .addModel(ModelMetadata.builder("UserOverrideModel").tableName("custom_users").build())
                    .build();
        }

        @Bean
        public GraphOrmClient graphOrmClient() {
            return CUSTOM_CLIENT;
        }
    }
}

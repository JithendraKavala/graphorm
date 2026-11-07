package com.graphorm.spring.boot.autoconfigure;

import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
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
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GraphOrmAutoConfiguration ApplicationContextRunner Tests (TASK-13.3, TASK-13.5)")
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

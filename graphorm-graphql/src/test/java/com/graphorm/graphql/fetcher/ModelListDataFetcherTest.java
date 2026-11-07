package com.graphorm.graphql.fetcher;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.graphql.binding.ArgumentBinder;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("[PERF-02] ModelListDataFetcher Unit & Max Limit Tests")
class ModelListDataFetcherTest {

    private final ModelMetadata model = ModelMetadata.builder("Student")
            .tableName("students")
            .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
            .addField(FieldMetadata.builder("name", String.class).build())
            .addPrimaryKeyField("id")
            .build();

    private final ModelRegistry registry = ModelRegistry.builder()
            .addModel(model)
            .build();

    private final DataSource dummyDataSource = (DataSource) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{DataSource.class},
            (proxy, method, args) -> {
                if ("getConnection".equals(method.getName())) {
                    return Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{Connection.class},
                            (cProxy, cMethod, cArgs) -> null
                    );
                }
                return null;
            }
    );

    @Test
    @DisplayName("Should apply default max limit of 100 when query has no pagination limit")
    void shouldApplyDefaultMaxLimit() {
        AtomicReference<SelectQuery> capturedQuery = new AtomicReference<>();
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, new PostgreSqlDialect()) {
            @Override
            public List<Map<String, Object>> queryForList(SelectQuery query) {
                capturedQuery.set(query);
                return List.of();
            }
        };

        ModelListDataFetcher fetcher = new ModelListDataFetcher(
                model,
                registry,
                executor,
                new ArgumentBinder(),
                new PostgreSqlDialect()
        );

        DataFetchingEnvironment env = (DataFetchingEnvironment) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DataFetchingEnvironment.class},
                (proxy, method, args) -> {
                    if ("getArguments".equals(method.getName())) {
                        return Collections.emptyMap();
                    }
                    if ("getArgument".equals(method.getName())) {
                        return null;
                    }
                    return null;
                }
        );

        fetcher.get(env);

        assertThat(capturedQuery.get()).isNotNull();
        assertThat(capturedQuery.get().limit()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should cap client limit if it exceeds maxPageSize")
    void shouldCapClientLimitExceedingMax() {
        AtomicReference<SelectQuery> capturedQuery = new AtomicReference<>();
        JdbcExecutor executor = new JdbcExecutor(dummyDataSource, new PostgreSqlDialect()) {
            @Override
            public List<Map<String, Object>> queryForList(SelectQuery query) {
                capturedQuery.set(query);
                return List.of();
            }
        };

        ModelListDataFetcher fetcher = new ModelListDataFetcher(
                model,
                registry,
                executor,
                new ArgumentBinder(),
                new PostgreSqlDialect(),
                50
        );

        DataFetchingEnvironment env = (DataFetchingEnvironment) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DataFetchingEnvironment.class},
                (proxy, method, args) -> {
                    if ("getArguments".equals(method.getName())) {
                        return Map.of("limit", 200);
                    }
                    if ("getArgument".equals(method.getName())) {
                        return "limit".equals(args[0]) ? 200 : null;
                    }
                    return null;
                }
        );

        fetcher.get(env);

        assertThat(capturedQuery.get()).isNotNull();
        assertThat(capturedQuery.get().limit()).isEqualTo(50);
    }
}

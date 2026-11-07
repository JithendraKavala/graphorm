package com.graphorm.graphql.loader;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.core.metadata.RelationType;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataLoaderMetricsListener Unit Tests")
class DataLoaderMetricsListenerTest {

    static class StubJdbcExecutor extends JdbcExecutor {
        StubJdbcExecutor() {
            super((DataSource) Proxy.newProxyInstance(
                    DataSource.class.getClassLoader(),
                    new Class<?>[]{DataSource.class},
                    (proxy, method, args) -> null
            ), new PostgreSqlDialect());
        }

        @Override
        public List<Map<String, Object>> queryForList(SelectQuery query) {
            return List.of(Map.of("id", 10, "department_id", 1, "name", "Engineering"));
        }
    }

    @Test
    @DisplayName("Should invoke DataLoaderMetricsListener when batch is loaded")
    void shouldInvokeMetricsListenerOnBatchLoad() throws Exception {
        JdbcExecutor executor = new StubJdbcExecutor();

        ModelMetadata student = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id", Integer.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("departmentId", Integer.class).dbColumnName("department_id").build())
                .addRelation(RelationMetadata.builder("department")
                        .sourceModel("Student")
                        .targetModel("Department")
                        .relationType(RelationType.MANY_TO_ONE)
                        .foreignKeyFields(List.of("departmentId"))
                        .referenceFields(List.of("id"))
                        .build())
                .build();

        ModelMetadata department = ModelMetadata.builder("Department")
                .tableName("departments")
                .addField(FieldMetadata.builder("id", Integer.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .build();

        ModelRegistry registry = ModelRegistry.builder()
                .addModel(student)
                .addModel(department)
                .build();

        AtomicInteger recordedSize = new AtomicInteger();
        AtomicLong recordedDuration = new AtomicLong();

        DataLoaderMetricsListener listener = (src, rel, size, duration) -> {
            recordedSize.set(size);
            recordedDuration.set(duration);
        };

        RelationMetadata rel = student.getRelation("department");
        BatchRelationDataLoader loader = new BatchRelationDataLoader(
                student,
                rel,
                registry,
                executor,
                new PostgreSqlDialect(),
                listener
        );

        loader.load(Set.of(1, 2, 3), null).toCompletableFuture().get();

        assertThat(recordedSize.get()).isEqualTo(3);
        assertThat(recordedDuration.get()).isGreaterThanOrEqualTo(0);
    }
}

package com.graphorm.graphql.loader;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.core.metadata.RelationType;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BatchRelationDataLoader Unit Tests (TASK-11.4)")
class BatchRelationDataLoaderTest {

    private ModelMetadata studentModel;
    private ModelMetadata departmentModel;
    private ModelRegistry modelRegistry;
    private TestJdbcExecutor testExecutor;

    static class TestJdbcExecutor extends JdbcExecutor {
        List<Map<String, Object>> returnedRows = List.of();
        int queryCallCount = 0;
        SelectQuery capturedQuery;

        TestJdbcExecutor() {
            super(createDummyDataSource(), new PostgreSqlDialect());
        }

        @Override
        public List<Map<String, Object>> queryForList(SelectQuery query) {
            this.queryCallCount++;
            this.capturedQuery = query;
            return returnedRows;
        }

        private static DataSource createDummyDataSource() {
            return (DataSource) Proxy.newProxyInstance(
                    DataSource.class.getClassLoader(),
                    new Class<?>[]{DataSource.class},
                    (proxy, method, args) -> null
            );
        }
    }

    @BeforeEach
    void setUp() {
        studentModel = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addField(FieldMetadata.builder("departmentId", Long.class).dbColumnName("department_id").build())
                .addPrimaryKeyField("id")
                .addRelation(RelationMetadata.builder("department")
                        .relationType(RelationType.MANY_TO_ONE)
                        .sourceModel("Student")
                        .targetModel("Department")
                        .foreignKeyField("departmentId")
                        .referenceField("id")
                        .build())
                .build();

        departmentModel = ModelMetadata.builder("Department")
                .tableName("departments")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addField(FieldMetadata.builder("code", String.class).build())
                .addPrimaryKeyField("id")
                .addRelation(RelationMetadata.builder("students")
                        .relationType(RelationType.ONE_TO_MANY)
                        .sourceModel("Department")
                        .targetModel("Student")
                        .build())
                .build();

        modelRegistry = ModelRegistry.builder()
                .addModel(studentModel)
                .addModel(departmentModel)
                .build();

        testExecutor = new TestJdbcExecutor();
    }

    @Test
    @DisplayName("TASK-11.4: DataLoaderRegistryFactory registers loaders for all entity relations")
    void shouldRegisterDataLoadersForAllRelations() {
        GraphOrmDataLoaderRegistryFactory factory = new GraphOrmDataLoaderRegistryFactory(
                modelRegistry,
                testExecutor,
                new PostgreSqlDialect()
        );

        DataLoaderRegistry registry = factory.create();
        assertThat(registry).isNotNull();

        DataLoader<Object, Object> studentDeptLoader = registry.getDataLoader("Student.department");
        assertThat(studentDeptLoader).isNotNull();

        DataLoader<Object, Object> deptStudentsLoader = registry.getDataLoader("Department.students");
        assertThat(deptStudentsLoader).isNotNull();
    }

    @Test
    @DisplayName("TASK-11.4: BatchRelationDataLoader loads to-one relation by keys")
    void shouldLoadToOneRelationByKeys() throws Exception {
        RelationMetadata deptRelation = studentModel.getRelation("department");
        BatchRelationDataLoader loader = new BatchRelationDataLoader(
                studentModel,
                deptRelation,
                modelRegistry,
                testExecutor,
                new PostgreSqlDialect()
        );

        testExecutor.returnedRows = List.of(
                Map.of("id", 10L, "name", "Computer Science"),
                Map.of("id", 20L, "name", "Mathematics")
        );

        BatchLoaderEnvironment env = BatchLoaderEnvironment.newBatchLoaderEnvironment().build();
        CompletableFuture<Map<Object, Object>> future = loader.load(Set.of(10L, 20L), env).toCompletableFuture();

        Map<Object, Object> results = future.get();
        assertThat(testExecutor.queryCallCount).isEqualTo(1);
        assertThat(results).hasSize(2);
        assertThat(((Map<?, ?>) results.get(10L)).get("name")).isEqualTo("Computer Science");
        assertThat(((Map<?, ?>) results.get(20L)).get("name")).isEqualTo("Mathematics");
    }

    @Test
    @DisplayName("TASK-11.4: BatchRelationDataLoader loads to-many relation by keys")
    void shouldLoadToManyRelationByKeys() throws Exception {
        RelationMetadata studentsRelation = departmentModel.getRelation("students");
        BatchRelationDataLoader loader = new BatchRelationDataLoader(
                departmentModel,
                studentsRelation,
                modelRegistry,
                testExecutor,
                new PostgreSqlDialect()
        );

        testExecutor.returnedRows = List.of(
                Map.of("id", 1L, "name", "Alice", "department_id", 10L),
                Map.of("id", 2L, "name", "Bob", "department_id", 10L),
                Map.of("id", 3L, "name", "Charlie", "department_id", 20L)
        );

        BatchLoaderEnvironment env = BatchLoaderEnvironment.newBatchLoaderEnvironment().build();
        CompletableFuture<Map<Object, Object>> future = loader.load(Set.of(10L, 20L), env).toCompletableFuture();

        Map<Object, Object> results = future.get();
        assertThat(testExecutor.queryCallCount).isEqualTo(1);
        assertThat(results).hasSize(2);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dept10Students = (List<Map<String, Object>>) results.get(10L);
        assertThat(dept10Students).hasSize(2);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dept20Students = (List<Map<String, Object>>) results.get(20L);
        assertThat(dept20Students).hasSize(1);
    }
}

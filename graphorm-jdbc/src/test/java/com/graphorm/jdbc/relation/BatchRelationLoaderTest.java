package com.graphorm.jdbc.relation;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.core.metadata.RelationType;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.mapper.RowMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BatchRelationLoader Unit Tests (TASK-6.2)")
class BatchRelationLoaderTest {

    public record StudentRecord(Long id, String name, Long departmentId) {}
    public record DepartmentRecord(Long id, String name) {}

    private ModelRegistry modelRegistry;
    private TestJdbcExecutor testExecutor;
    private BatchRelationLoader loader;

    static class TestJdbcExecutor extends JdbcExecutor {
        List<?> returnedRows = List.of();
        int queryCallCount = 0;
        SelectQuery capturedQuery;

        TestJdbcExecutor() {
            super(createDummyDataSource(), new PostgreSqlDialect());
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> queryForList(SelectQuery query, RowMapper<T> rowMapper) {
            this.queryCallCount++;
            this.capturedQuery = query;
            return (List<T>) returnedRows;
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
        ModelMetadata department = ModelMetadata.builder("Department")
                .tableName("departments")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addPrimaryKeyField("id")
                .addRelation(RelationMetadata.builder("students")
                        .relationType(RelationType.ONE_TO_MANY)
                        .sourceModel("Department")
                        .targetModel("Student")
                        .build())
                .build();

        ModelMetadata student = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addField(FieldMetadata.builder("departmentId", Long.class).dbColumnName("department_id").nullable(true).build())
                .addPrimaryKeyField("id")
                .addRelation(RelationMetadata.builder("department")
                        .relationType(RelationType.MANY_TO_ONE)
                        .sourceModel("Student")
                        .targetModel("Department")
                        .foreignKeyField("departmentId")
                        .referenceField("id")
                        .build())
                .build();

        modelRegistry = ModelRegistry.builder()
                .addModel(department)
                .addModel(student)
                .build();

        testExecutor = new TestJdbcExecutor();
        loader = new BatchRelationLoader(testExecutor, modelRegistry);
    }

    @Test
    @DisplayName("Should load Many-to-One relations and associate back to parent records")
    void shouldLoadManyToOneAndAssociate() {
        StudentRecord s1 = new StudentRecord(1L, "Alice", 10L);
        StudentRecord s2 = new StudentRecord(2L, "Bob", 20L);
        StudentRecord s3 = new StudentRecord(3L, "Charlie", 10L);
        List<StudentRecord> students = List.of(s1, s2, s3);

        DepartmentRecord d1 = new DepartmentRecord(10L, "Computer Science");
        DepartmentRecord d2 = new DepartmentRecord(20L, "Mathematics");

        testExecutor.returnedRows = List.of(d1, d2);

        BatchLoadResult<StudentRecord, DepartmentRecord> result = loader.load(
                "Student", "department", students, DepartmentRecord.class);

        assertThat(testExecutor.queryCallCount).isEqualTo(1);
        assertThat(result.getOne(s1)).contains(d1);
        assertThat(result.getOne(s2)).contains(d2);
        assertThat(result.getOne(s3)).contains(d1);
    }

    @Test
    @DisplayName("Should load One-to-Many relations and associate back to parent records")
    void shouldLoadOneToManyAndAssociate() {
        DepartmentRecord d1 = new DepartmentRecord(10L, "Computer Science");
        DepartmentRecord d2 = new DepartmentRecord(20L, "Mathematics");
        List<DepartmentRecord> departments = List.of(d1, d2);

        StudentRecord s1 = new StudentRecord(1L, "Alice", 10L);
        StudentRecord s2 = new StudentRecord(2L, "Charlie", 10L);
        StudentRecord s3 = new StudentRecord(3L, "Bob", 20L);

        testExecutor.returnedRows = List.of(s1, s2, s3);

        BatchLoadResult<DepartmentRecord, StudentRecord> result = loader.load(
                "Department", "students", departments, StudentRecord.class);

        assertThat(testExecutor.queryCallCount).isEqualTo(1);
        assertThat(result.getMany(d1)).containsExactly(s1, s2);
        assertThat(result.getMany(d2)).containsExactly(s3);
    }

    @Test
    @DisplayName("Should load and attach relations directly into parent Maps")
    void shouldLoadAndAttachToParentMaps() {
        Map<String, Object> p1 = new HashMap<>();
        p1.put("id", 1L);
        p1.put("name", "Alice");
        p1.put("departmentId", 10L);

        Map<String, Object> p2 = new HashMap<>();
        p2.put("id", 2L);
        p2.put("name", "David");
        p2.put("departmentId", null); // null relation

        List<Map<String, Object>> parents = List.of(p1, p2);

        Map<String, Object> dept10 = Map.of("id", 10L, "name", "Computer Science");
        testExecutor.returnedRows = List.of(dept10);

        loader.loadAndAttach("Student", "department", parents);

        assertThat(testExecutor.queryCallCount).isEqualTo(1);
        assertThat(p1.get("department")).isEqualTo(dept10);
        assertThat(p2.get("department")).isNull();
    }

    @Test
    @DisplayName("Should handle empty parent lists or null keys with 0 SQL queries")
    void shouldHandleEmptyParentsWithoutQuerying() {
        BatchLoadResult<StudentRecord, DepartmentRecord> resEmpty = loader.load(
                "Student", "department", List.of(), DepartmentRecord.class);
        assertThat(resEmpty.oneToOne()).isEmpty();

        StudentRecord sNull = new StudentRecord(1L, "Ghost", null);
        BatchLoadResult<StudentRecord, DepartmentRecord> resNull = loader.load(
                "Student", "department", List.of(sNull), DepartmentRecord.class);
        assertThat(resNull.oneToOne()).isEmpty();

        assertThat(testExecutor.queryCallCount).isZero();
    }
}

package com.graphorm.jdbc;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.core.metadata.RelationType;
import com.graphorm.core.query.ProjectedColumn;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.relation.BatchLoadResult;
import com.graphorm.jdbc.relation.BatchRelationLoader;
import com.graphorm.jdbc.util.SqlStatementCounter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end Testcontainers integration test verifying that batched relation loading
 * eliminates N+1 queries, asserting exact statement counts (1 primary + 1 batch = 2 SQL queries)
 * across 1:1, 1:N, and N:1 relations (TASK-6.4).
 */
@DisplayName("PostgreSQL Batch Relation Loading & Zero N+1 Integration Tests (TASK-6.4)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PostgresBatchRelationLoadingIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PostgresBatchRelationLoadingIntegrationTest.class);

    public record DepartmentRecord(Long id, String name, String code) {}
    public record StudentRecord(Long id, String name, String email, Long departmentId) {}
    public record ProfileRecord(Long id, Long studentId, String bio) {}

    private static PostgreSQLContainer<?> container;
    private static DataSource rawDataSource;
    private static SqlStatementCounter statementCounter;
    private static JdbcExecutor executor;
    private static PostgreSqlDialect dialect;
    private static ModelRegistry modelRegistry;
    private static BatchRelationLoader batchLoader;

    @BeforeAll
    static void setUpAll() throws Exception {
        dialect = new PostgreSqlDialect();

        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            log.info("Docker daemon not accessible: {}. Falling back to local PostgreSQL instance.", t.getMessage());
        }

        if (dockerAvailable) {
            log.info("Starting Testcontainers PostgreSQL 16 container for Phase 6 tests...");
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("graphorm_phase6")
                    .withUsername("phase6_user")
                    .withPassword("phase6_pass");
            container.start();

            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setUrl(container.getJdbcUrl());
            ds.setUser(container.getUsername());
            ds.setPassword(container.getPassword());
            rawDataSource = ds;
        } else {
            log.info("Connecting to local PostgreSQL instance on localhost:5432...");
            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setServerNames(new String[]{"localhost"});
            ds.setPortNumbers(new int[]{5432});
            ds.setUser("postgres");
            ds.setPassword("");
            ds.setDatabaseName("postgres");
            rawDataSource = ds;
        }

        // Wrap DataSource with SqlStatementCounter to track statement executions
        statementCounter = new SqlStatementCounter();
        DataSource countingDataSource = statementCounter.wrap(rawDataSource);
        executor = new JdbcExecutor(countingDataSource, dialect);

        // Initialize database tables
        try (Connection conn = rawDataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("DROP TABLE IF EXISTS student_profiles_p6 CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS students_p6 CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS departments_p6 CASCADE;");

            stmt.execute("""
                CREATE TABLE departments_p6 (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(255) NOT NULL UNIQUE,
                    code VARCHAR(50) NOT NULL
                );
            """);

            stmt.execute("""
                CREATE TABLE students_p6 (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    department_id BIGINT REFERENCES departments_p6(id)
                );
            """);

            stmt.execute("""
                CREATE TABLE student_profiles_p6 (
                    id BIGINT PRIMARY KEY,
                    student_id BIGINT NOT NULL UNIQUE REFERENCES students_p6(id),
                    bio TEXT NOT NULL
                );
            """);

            // Seed 5 Departments
            stmt.execute("""
                INSERT INTO departments_p6 (id, name, code) VALUES
                (1, 'Computer Science', 'CS'),
                (2, 'Mathematics', 'MATH'),
                (3, 'Physics', 'PHYS'),
                (4, 'Chemistry', 'CHEM'),
                (5, 'Biology', 'BIO');
            """);

            // Seed exactly 100 students and 100 corresponding profiles
            StringBuilder studentInsert = new StringBuilder("INSERT INTO students_p6 (id, name, email, department_id) VALUES ");
            StringBuilder profileInsert = new StringBuilder("INSERT INTO student_profiles_p6 (id, student_id, bio) VALUES ");

            for (int i = 1; i <= 100; i++) {
                if (i > 1) {
                    studentInsert.append(", ");
                    profileInsert.append(", ");
                }
                long deptId = ((i - 1) % 5) + 1; // 20 students per department
                studentInsert.append(String.format("(%d, 'Student %03d', 'student%03d@university.edu', %d)", i, i, i, deptId));
                profileInsert.append(String.format("(%d, %d, 'Bio for student %03d')", i, i, i));
            }
            studentInsert.append(";");
            profileInsert.append(";");

            stmt.execute(studentInsert.toString());
            stmt.execute(profileInsert.toString());
        }

        // Configure ModelRegistry
        ModelMetadata departmentMeta = ModelMetadata.builder("Department")
                .tableName("departments_p6")
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

        ModelMetadata studentMeta = ModelMetadata.builder("Student")
                .tableName("students_p6")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addField(FieldMetadata.builder("email", String.class).unique(true).build())
                .addField(FieldMetadata.builder("departmentId", Long.class).dbColumnName("department_id").nullable(true).build())
                .addPrimaryKeyField("id")
                .addRelation(RelationMetadata.builder("department")
                        .relationType(RelationType.MANY_TO_ONE)
                        .sourceModel("Student")
                        .targetModel("Department")
                        .foreignKeyField("departmentId")
                        .referenceField("id")
                        .build())
                .addRelation(RelationMetadata.builder("profile")
                        .relationType(RelationType.ONE_TO_ONE)
                        .sourceModel("Student")
                        .targetModel("Profile")
                        .foreignKeyField("id")
                        .referenceField("studentId")
                        .build())
                .build();

        ModelMetadata profileMeta = ModelMetadata.builder("Profile")
                .tableName("student_profiles_p6")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("studentId", Long.class).dbColumnName("student_id").build())
                .addField(FieldMetadata.builder("bio", String.class).build())
                .addPrimaryKeyField("id")
                .addRelation(RelationMetadata.builder("student")
                        .relationType(RelationType.ONE_TO_ONE)
                        .sourceModel("Profile")
                        .targetModel("Student")
                        .foreignKeyField("studentId")
                        .referenceField("id")
                        .build())
                .build();

        modelRegistry = ModelRegistry.builder()
                .addModel(departmentMeta)
                .addModel(studentMeta)
                .addModel(profileMeta)
                .build();

        batchLoader = new BatchRelationLoader(executor, modelRegistry);
    }

    @AfterAll
    static void tearDownAll() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    @Test
    @Order(1)
    @DisplayName("TASK-6.4: Loading 100 students with departments executes in EXACTLY 2 SQL queries (0 N+1 regression)")
    void shouldLoad100StudentsWithDepartmentsInExactlyTwoQueries() {
        statementCounter.reset();

        // 1. Primary Query: Load 100 students
        SelectQuery selectStudents = SelectQuery.from("students_p6")
                .column(ProjectedColumn.of("id", "id"))
                .column(ProjectedColumn.of("name", "name"))
                .column(ProjectedColumn.of("email", "email"))
                .column(ProjectedColumn.of("department_id", "departmentId"))
                .build();

        List<StudentRecord> students = executor.queryForList(selectStudents, StudentRecord.class);
        assertThat(students).hasSize(100);

        // Verify exactly 1 SQL statement executed so far
        statementCounter.assertTotalCount(1);
        statementCounter.assertSelectCount(1);

        // 2. Batch Relation Query: Load departments for all 100 students
        BatchLoadResult<StudentRecord, DepartmentRecord> result = batchLoader.load(
                "Student", "department", students, DepartmentRecord.class);

        // Verify EXACTLY 2 SQL statements executed in total!
        statementCounter.assertTotalCount(2);
        statementCounter.assertSelectCount(2);

        // 3. Verify correctness of in-memory associations
        for (StudentRecord student : students) {
            assertThat(result.getOne(student)).isPresent();
            DepartmentRecord dept = result.getOne(student).get();
            assertThat(dept.id()).isEqualTo(student.departmentId());
            long expectedDeptId = ((student.id() - 1) % 5) + 1;
            assertThat(dept.id()).isEqualTo(expectedDeptId);
        }
    }

    @Test
    @Order(2)
    @DisplayName("TASK-6.4: Loading 5 departments with all their students executes in EXACTLY 2 SQL queries")
    void shouldLoadDepartmentsWithStudentsInExactlyTwoQueries() {
        statementCounter.reset();

        // 1. Primary Query: Load 5 departments
        SelectQuery selectDepts = SelectQuery.from("departments_p6")
                .column(ProjectedColumn.of("id", "id"))
                .column(ProjectedColumn.of("name", "name"))
                .column(ProjectedColumn.of("code", "code"))
                .build();

        List<DepartmentRecord> departments = executor.queryForList(selectDepts, DepartmentRecord.class);
        assertThat(departments).hasSize(5);

        statementCounter.assertTotalCount(1);

        // 2. Batch Relation Query: Load 1:N students for the 5 departments
        BatchLoadResult<DepartmentRecord, StudentRecord> result = batchLoader.load(
                "Department", "students", departments, StudentRecord.class);

        // Assert exactly 2 SQL statements executed
        statementCounter.assertTotalCount(2);
        statementCounter.assertSelectCount(2);

        // Verify that all 100 students are correctly partitioned (20 per department)
        int totalAssociatedStudents = 0;
        for (DepartmentRecord dept : departments) {
            List<StudentRecord> deptStudents = result.getMany(dept);
            assertThat(deptStudents).hasSize(20);
            for (StudentRecord student : deptStudents) {
                assertThat(student.departmentId()).isEqualTo(dept.id());
            }
            totalAssociatedStudents += deptStudents.size();
        }
        assertThat(totalAssociatedStudents).isEqualTo(100);
    }

    @Test
    @Order(3)
    @DisplayName("TASK-6.4: Loading 1:1 student profiles executes in EXACTLY 2 SQL queries")
    void shouldLoad1ToOneProfilesInExactlyTwoQueries() {
        statementCounter.reset();

        // 1. Load 100 students
        SelectQuery selectStudents = SelectQuery.from("students_p6")
                .column(ProjectedColumn.of("id", "id"))
                .column(ProjectedColumn.of("name", "name"))
                .column(ProjectedColumn.of("email", "email"))
                .column(ProjectedColumn.of("department_id", "departmentId"))
                .build();

        List<StudentRecord> students = executor.queryForList(selectStudents, StudentRecord.class);
        assertThat(students).hasSize(100);

        statementCounter.assertTotalCount(1);

        // 2. Batch Load 1:1 Profiles
        BatchLoadResult<StudentRecord, ProfileRecord> result = batchLoader.load(
                "Student", "profile", students, ProfileRecord.class);

        // Assert exactly 2 SQL statements executed
        statementCounter.assertTotalCount(2);
        statementCounter.assertSelectCount(2);

        for (StudentRecord student : students) {
            assertThat(result.getOne(student)).isPresent();
            ProfileRecord profile = result.getOne(student).get();
            assertThat(profile.studentId()).isEqualTo(student.id());
            assertThat(profile.bio()).isEqualTo(String.format("Bio for student %03d", student.id()));
        }
    }

    @Test
    @Order(4)
    @DisplayName("TASK-6.4: loadAndAttach populates relation directly into parent Maps in EXACTLY 2 SQL queries")
    void shouldLoadAndAttachToParentMapsInExactlyTwoQueries() {
        statementCounter.reset();

        // 1. Primary Query: Load 100 students as Maps
        SelectQuery selectStudents = SelectQuery.from("students_p6")
                .column(ProjectedColumn.of("id", "id"))
                .column(ProjectedColumn.of("name", "name"))
                .column(ProjectedColumn.of("email", "email"))
                .column(ProjectedColumn.of("department_id", "departmentId"))
                .build();

        List<Map<String, Object>> studentMaps = executor.queryForList(selectStudents);
        assertThat(studentMaps).hasSize(100);
        statementCounter.assertTotalCount(1);

        // 2. Attach departments
        batchLoader.loadAndAttach("Student", "department", studentMaps);

        statementCounter.assertTotalCount(2);
        statementCounter.assertSelectCount(2);

        for (Map<String, Object> sMap : studentMaps) {
            assertThat(sMap).containsKey("department");
            @SuppressWarnings("unchecked")
            Map<String, Object> deptMap = (Map<String, Object>) sMap.get("department");
            assertThat(deptMap).isNotNull();
            assertThat(((Number) deptMap.get("id")).longValue())
                    .isEqualTo(((Number) sMap.get("departmentId")).longValue());
        }
    }

    @Test
    @Order(5)
    @DisplayName("TASK-6.4: Empty parent collection or null keys executes 0 additional queries")
    void shouldNotExecuteQueryForEmptyOrNullParents() {
        statementCounter.reset();

        BatchLoadResult<StudentRecord, DepartmentRecord> emptyResult = batchLoader.load(
                "Student", "department", List.of(), DepartmentRecord.class);
        assertThat(emptyResult.oneToOne()).isEmpty();

        statementCounter.assertTotalCount(0);
    }
}

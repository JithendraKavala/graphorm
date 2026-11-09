package com.graphorm.example.university.service;

import com.graphorm.core.query.BatchInsertQuery;
import com.graphorm.core.query.UpsertQuery;
import com.graphorm.core.tenant.TenantContext;
import com.graphorm.example.university.generated.client.GraphOrmClient;
import com.graphorm.example.university.generated.input.CourseCreateInput;
import com.graphorm.example.university.generated.model.AuditLog;
import com.graphorm.example.university.generated.model.AuditSummary;
import com.graphorm.example.university.generated.model.Course;
import com.graphorm.example.university.generated.model.Department;
import com.graphorm.example.university.generated.model.DepartmentStats;
import com.graphorm.example.university.generated.model.Student;
import com.graphorm.example.university.generated.model.StudentDashboard;
import com.graphorm.example.university.web.dto.CreateCourseRequest;
import com.graphorm.example.university.web.dto.UpdateStudentOptimisticRequest;
import com.graphorm.example.university.web.dto.UpsertStudentRequest;
import com.graphorm.jdbc.builder.UpdateBuilder;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service demonstrating GraphORM type-safe client APIs, advanced query features,
 * optimistic locking, and multi-tenant execution.
 */
@Service
public class UniversityService {

    private final GraphOrmClient db;
    private final JdbcExecutor executor;
    private final SqlDialect dialect;

    public UniversityService(GraphOrmClient db, JdbcExecutor executor, SqlDialect dialect) {
        this.db = db;
        this.executor = executor;
        this.dialect = dialect;
    }

    /**
     * Retrieves all departments with type-safe ordering.
     */
    @Transactional(readOnly = true)
    public List<Department> getAllDepartments() {
        return db.department().findMany(q -> q.orderBy(f -> f.name().asc()));
    }

    /**
     * Retrieves all students with type-safe ordering.
     */
    @Transactional(readOnly = true)
    public List<Student> getAllStudents() {
        return db.student().findMany(q -> q.orderBy(f -> f.name().asc()));
    }

    /**
     * Retrieves student by ID.
     */
    @Transactional(readOnly = true)
    public Optional<Student> getStudentById(int id) {
        return db.student().findById(id);
    }

    /**
     * Demonstrates Upsert: Inserts student or updates on unique email conflict.
     */
    @Transactional
    public Student upsertStudent(UpsertStudentRequest request) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", request.name());
        values.put("email", request.email());
        if (request.gpa() != null) values.put("gpa", request.gpa());
        if (request.departmentId() != null) values.put("department_id", request.departmentId());
        values.put("version", 1);

        Map<String, Object> updateValues = new LinkedHashMap<>();
        updateValues.put("name", request.name());
        if (request.gpa() != null) updateValues.put("gpa", request.gpa());
        if (request.departmentId() != null) updateValues.put("department_id", request.departmentId());

        UpsertQuery query = UpsertQuery.into("students")
                .values(values)
                .onConflict("email")
                .doUpdate(updateValues)
                .returningAll()
                .build();

        return executor.executeUpsertAndReturn(query, Student.class)
                .orElseThrow(() -> new IllegalStateException("Failed to upsert student"));
    }

    /**
     * Demonstrates @version Optimistic Locking: Throws OptimisticLockException
     * if expectedVersion does not match the database version.
     */
    @Transactional
    public Student updateStudentOptimistic(int id, UpdateStudentOptimisticRequest request) {
        UpdateBuilder ub = UpdateBuilder.of(dialect)
                .table("students")
                .id("id", id)
                .optimisticLock("version", request.expectedVersion());

        if (request.name() != null) ub.set("name", request.name());
        if (request.gpa() != null) ub.set("gpa", request.gpa());

        executor.executeUpdate(ub);
        return db.student().findById(id).orElseThrow();
    }

    /**
     * Retrieves all courses.
     */
    @Transactional(readOnly = true)
    public List<Course> getAllCourses() {
        return db.course().findMany(q -> q.orderBy(f -> f.title().asc()));
    }

    /**
     * Creates a new course via type-safe CourseCreateInput.
     */
    @Transactional
    public Course createCourse(CreateCourseRequest request) {
        CourseCreateInput input = CourseCreateInput.builder()
                .title(request.title())
                .code(request.code())
                .credits(request.credits())
                .departmentId(request.departmentId())
                .instructorId(request.instructorId())
                .build();
        return db.course().create(input);
    }

    /**
     * Demonstrates Batch Operations: Inserts multiple courses efficiently in a single batch query.
     */
    @Transactional
    public List<Course> batchCreateCourses(List<CreateCourseRequest> requests) {
        List<List<Object>> rows = requests.stream()
                .map(r -> List.<Object>of(
                        r.title(),
                        r.code(),
                        r.credits(),
                        r.departmentId(),
                        r.instructorId()
                ))
                .toList();

        BatchInsertQuery query = BatchInsertQuery.into("courses")
                .columns("title", "code", "credits", "department_id", "instructor_id")
                .rows(rows)
                .returningAll()
                .build();

        return executor.executeBatchInsertAndReturn(query, Course.class);
    }

    /**
     * Queries the database-backed physical view (Stage 1 view model).
     */
    @Transactional(readOnly = true)
    public List<DepartmentStats> getDepartmentStats() {
        return db.departmentStats().findMany(q -> q.orderBy(f -> f.departmentName().asc()));
    }

    /**
     * Queries the GraphORM-defined virtual aggregated view (Stage 2 view model).
     * Backed by @cached(ttl = "60s", tags = ["Student", "Enrollment", "Department"]).
     */
    @Transactional(readOnly = true)
    public List<StudentDashboard> getStudentDashboards() {
        return db.studentDashboard().findMany(q -> q.orderBy(f -> f.studentName().asc()));
    }

    /**
     * Queries virtual view for a single student.
     */
    @Transactional(readOnly = true)
    public Optional<StudentDashboard> getStudentDashboard(int studentId) {
        return db.studentDashboard().findFirst(q -> q.where(f -> f.studentId().eq(studentId)));
    }

    /**
     * Demonstrates Multi-Tenancy: Retrieves audit logs isolated by tenantId via TenantContext.
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLogsForTenant(String tenantId) {
        return TenantContext.supplyAsTenant(tenantId, () ->
                db.auditLog().findMany(q -> q.where(f -> f.tenantId().eq(tenantId)))
        );
    }

    /**
     * Retrieves audit summaries view.
     */
    @Transactional(readOnly = true)
    public List<AuditSummary> getAuditSummaries() {
        return db.auditSummary().findMany();
    }
}

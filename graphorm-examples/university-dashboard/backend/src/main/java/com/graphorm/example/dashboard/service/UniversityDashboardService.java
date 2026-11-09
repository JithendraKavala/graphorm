package com.graphorm.example.dashboard.service;

import com.graphorm.core.query.WherePredicate;
import com.graphorm.core.query.BatchInsertQuery;
import com.graphorm.core.query.UpsertQuery;
import com.graphorm.core.tenant.TenantContext;
import com.graphorm.example.dashboard.generated.model.AuditLog;
import com.graphorm.example.dashboard.generated.model.AuditSummary;
import com.graphorm.example.dashboard.web.dto.UpsertStudentRequest;
import com.graphorm.jdbc.builder.UpdateBuilder;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.JdbcExecutor;
import java.util.LinkedHashMap;

import com.graphorm.example.dashboard.generated.client.GraphOrmClient;
import com.graphorm.example.dashboard.generated.filter.CourseFilter;
import com.graphorm.example.dashboard.generated.filter.DepartmentFilter;
import com.graphorm.example.dashboard.generated.filter.EnrollmentFilter;
import com.graphorm.example.dashboard.generated.filter.StudentDashboardFilter;
import com.graphorm.example.dashboard.generated.filter.StudentFilter;
import com.graphorm.example.dashboard.generated.input.CourseCreateInput;
import com.graphorm.example.dashboard.generated.input.CourseUpdateInput;
import com.graphorm.example.dashboard.generated.input.DepartmentCreateInput;
import com.graphorm.example.dashboard.generated.input.DepartmentUpdateInput;
import com.graphorm.example.dashboard.generated.input.EnrollmentCreateInput;
import com.graphorm.example.dashboard.generated.input.EnrollmentUpdateInput;
import com.graphorm.example.dashboard.generated.input.InstructorCreateInput;
import com.graphorm.example.dashboard.generated.input.InstructorUpdateInput;
import com.graphorm.example.dashboard.generated.input.StudentCreateInput;
import com.graphorm.example.dashboard.generated.input.StudentUpdateInput;
import com.graphorm.example.dashboard.generated.model.Course;
import com.graphorm.example.dashboard.generated.model.Department;
import com.graphorm.example.dashboard.generated.model.DepartmentStats;
import com.graphorm.example.dashboard.generated.model.Enrollment;
import com.graphorm.example.dashboard.generated.model.Instructor;
import com.graphorm.example.dashboard.generated.model.Student;
import com.graphorm.example.dashboard.generated.model.StudentDashboard;
import com.graphorm.example.dashboard.web.dto.CreateCourseRequest;
import com.graphorm.example.dashboard.web.dto.CreateDepartmentRequest;
import com.graphorm.example.dashboard.web.dto.CreateEnrollmentRequest;
import com.graphorm.example.dashboard.web.dto.CreateInstructorRequest;
import com.graphorm.example.dashboard.web.dto.CreateStudentRequest;
import com.graphorm.example.dashboard.web.dto.PaginatedResponse;
import com.graphorm.example.dashboard.web.dto.UpdateCourseRequest;
import com.graphorm.example.dashboard.web.dto.UpdateDepartmentRequest;
import com.graphorm.example.dashboard.web.dto.UpdateEnrollmentRequest;
import com.graphorm.example.dashboard.web.dto.UpdateInstructorRequest;
import com.graphorm.example.dashboard.web.dto.UpdateStudentRequest;
import com.graphorm.generator.client.QuerySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Service demonstrating GraphORM type-safe client APIs for models and view models
 * supporting the University React Dashboard with server-side pagination, sorting, and filtering.
 */
@Service
public class UniversityDashboardService {

    private final GraphOrmClient db;
    private final JdbcExecutor executor;
    private final SqlDialect dialect;

    public UniversityDashboardService(GraphOrmClient db, JdbcExecutor executor, SqlDialect dialect) {
        this.db = db;
        this.executor = executor;
        this.dialect = dialect;
    }

    // ==========================================
    // DEPARTMENTS
    // ==========================================

    @Transactional(readOnly = true)
    public List<Department> getAllDepartments() {
        return db.department().findMany(q -> q.orderBy(f -> f.name().asc()));
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<Department> getDepartments(
            int page,
            int pageSize,
            String search,
            String sortBy,
            String sortOrder
    ) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        int offset = (safePage - 1) * safePageSize;

        Consumer<QuerySpec<DepartmentFilter>> filterConsumer = q -> {
            if (search != null && !search.isBlank()) {
                String term = search.trim();
                q.where(WherePredicate.or(
                        WherePredicate.iContains("name", term),
                        WherePredicate.iContains("code", term)
                ));
            }
        };

        List<Department> items = db.department().findMany(q -> {
            filterConsumer.accept(q);
            boolean isDesc = "desc".equalsIgnoreCase(sortOrder);
            if ("code".equalsIgnoreCase(sortBy)) {
                q.orderBy(isDesc ? f -> f.code().desc() : f -> f.code().asc());
            } else if ("id".equalsIgnoreCase(sortBy)) {
                q.orderBy(isDesc ? f -> f.id().desc() : f -> f.id().asc());
            } else {
                q.orderBy(isDesc ? f -> f.name().desc() : f -> f.name().asc());
            }
            q.limit(safePageSize);
            q.offset(offset);
        });

        long total = db.department().count(filterConsumer);
        return new PaginatedResponse<>(items, total, safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public Optional<Department> getDepartmentById(int id) {
        return db.department().findById(id);
    }

    @Transactional
    public Department createDepartment(CreateDepartmentRequest req) {
        DepartmentCreateInput input = DepartmentCreateInput.builder()
                .name(req.name())
                .code(req.code())
                .build();
        return db.department().create(input);
    }

    @Transactional
    public Department updateDepartment(int id, UpdateDepartmentRequest req) {
        DepartmentUpdateInput input = DepartmentUpdateInput.builder()
                .name(req.name())
                .code(req.code())
                .build();
        return db.department().update(id, input);
    }

    @Transactional
    public boolean deleteDepartment(int id) {
        return db.department().delete(id);
    }

    // ==========================================
    // STUDENTS
    // ==========================================

    @Transactional(readOnly = true)
    public List<Student> getAllStudents() {
        return db.student().findMany(q -> q.orderBy(f -> f.name().asc()));
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<Student> getStudents(
            int page,
            int pageSize,
            String search,
            Integer departmentId,
            Double minGpa,
            String sortBy,
            String sortOrder
    ) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        int offset = (safePage - 1) * safePageSize;

        Consumer<QuerySpec<StudentFilter>> filterConsumer = q -> {
            if (search != null && !search.isBlank()) {
                String term = search.trim();
                q.where(WherePredicate.or(
                        WherePredicate.iContains("name", term),
                        WherePredicate.iContains("email", term)
                ));
            }
            if (departmentId != null) {
                q.where(WherePredicate.eq("department_id", departmentId));
            }
            if (minGpa != null && minGpa > 0.0) {
                q.where(WherePredicate.gte("gpa", minGpa));
            }
        };

        List<Student> items = db.student().findMany(q -> {
            filterConsumer.accept(q);
            boolean isDesc = "desc".equalsIgnoreCase(sortOrder);
            if ("gpa".equalsIgnoreCase(sortBy)) {
                q.orderBy(isDesc ? f -> f.gpa().desc() : f -> f.gpa().asc());
            } else if ("id".equalsIgnoreCase(sortBy)) {
                q.orderBy(isDesc ? f -> f.id().desc() : f -> f.id().asc());
            } else {
                q.orderBy(isDesc ? f -> f.name().desc() : f -> f.name().asc());
            }
            q.limit(safePageSize);
            q.offset(offset);
        });

        long total = db.student().count(filterConsumer);
        return new PaginatedResponse<>(items, total, safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public Optional<Student> getStudentById(int id) {
        return db.student().findById(id);
    }

    @Transactional
    public Student createStudent(CreateStudentRequest req) {
        StudentCreateInput input = StudentCreateInput.builder()
                .name(req.name())
                .email(req.email())
                .gpa(req.gpa())
                .departmentId(req.departmentId())
                .build();
        return db.student().create(input);
    }

    @Transactional
    public Student updateStudent(int id, UpdateStudentRequest req) {
        if (req.version() != null) {
            UpdateBuilder ub = UpdateBuilder.of(dialect)
                    .table("students")
                    .id("id", id)
                    .optimisticLock("version", req.version());
            if (req.name() != null) ub.set("name", req.name());
            if (req.email() != null) ub.set("email", req.email());
            if (req.gpa() != null) ub.set("gpa", req.gpa());
            if (req.departmentId() != null) ub.set("department_id", req.departmentId());
            executor.executeUpdate(ub);
            return db.student().findById(id).orElseThrow();
        }
        StudentUpdateInput input = StudentUpdateInput.builder()
                .name(req.name())
                .email(req.email())
                .gpa(req.gpa())
                .departmentId(req.departmentId())
                .build();
        return db.student().update(id, input);
    }

    @Transactional
    public Student upsertStudent(UpsertStudentRequest req) {
        java.util.Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", req.name());
        values.put("email", req.email());
        if (req.gpa() != null) values.put("gpa", req.gpa());
        if (req.departmentId() != null) values.put("department_id", req.departmentId());
        values.put("version", 1);

        java.util.Map<String, Object> updateValues = new LinkedHashMap<>();
        updateValues.put("name", req.name());
        if (req.gpa() != null) updateValues.put("gpa", req.gpa());
        if (req.departmentId() != null) updateValues.put("department_id", req.departmentId());

        UpsertQuery query = UpsertQuery.into("students")
                .values(values)
                .onConflict("email")
                .doUpdate(updateValues)
                .returningAll()
                .build();

        return executor.executeUpsertAndReturn(query, Student.class)
                .orElseThrow(() -> new IllegalStateException("Failed to upsert student"));
    }

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

    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLogsForTenant(String tenantId) {
        return TenantContext.supplyAsTenant(tenantId, () ->
                db.auditLog().findMany(q -> q.where(f -> f.tenantId().eq(tenantId)))
        );
    }

    @Transactional(readOnly = true)
    public List<AuditSummary> getAuditSummaries() {
        return db.auditSummary().findMany();
    }

    @Transactional
    public boolean deleteStudent(int id) {
        return db.student().delete(id);
    }

    // ==========================================
    // COURSES
    // ==========================================

    @Transactional(readOnly = true)
    public List<Course> getAllCourses() {
        return db.course().findMany(q -> q.orderBy(f -> f.title().asc()));
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<Course> getCourses(
            int page,
            int pageSize,
            String search,
            Integer departmentId,
            Integer instructorId,
            Integer credits,
            String sortBy,
            String sortOrder
    ) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        int offset = (safePage - 1) * safePageSize;

        Consumer<QuerySpec<CourseFilter>> filterConsumer = q -> {
            if (search != null && !search.isBlank()) {
                String term = search.trim();
                q.where(WherePredicate.or(
                        WherePredicate.iContains("title", term),
                        WherePredicate.iContains("code", term)
                ));
            }
            if (departmentId != null) {
                q.where(WherePredicate.eq("department_id", departmentId));
            }
            if (instructorId != null) {
                q.where(WherePredicate.eq("instructor_id", instructorId));
            }
            if (credits != null && credits > 0) {
                q.where(WherePredicate.eq("credits", credits));
            }
        };

        List<Course> items = db.course().findMany(q -> {
            filterConsumer.accept(q);
            boolean isDesc = "desc".equalsIgnoreCase(sortOrder);
            if ("title".equalsIgnoreCase(sortBy)) {
                q.orderBy(isDesc ? f -> f.title().desc() : f -> f.title().asc());
            } else if ("credits".equalsIgnoreCase(sortBy)) {
                q.orderBy(isDesc ? f -> f.credits().desc() : f -> f.credits().asc());
            } else if ("id".equalsIgnoreCase(sortBy)) {
                q.orderBy(isDesc ? f -> f.id().desc() : f -> f.id().asc());
            } else {
                q.orderBy(isDesc ? f -> f.code().desc() : f -> f.code().asc());
            }
            q.limit(safePageSize);
            q.offset(offset);
        });

        long total = db.course().count(filterConsumer);
        return new PaginatedResponse<>(items, total, safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public Optional<Course> getCourseById(int id) {
        return db.course().findById(id);
    }

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

    @Transactional
    public Course updateCourse(int id, UpdateCourseRequest req) {
        CourseUpdateInput input = CourseUpdateInput.builder()
                .title(req.title())
                .code(req.code())
                .credits(req.credits())
                .departmentId(req.departmentId())
                .instructorId(req.instructorId())
                .build();
        return db.course().update(id, input);
    }

    @Transactional
    public boolean deleteCourse(int id) {
        return db.course().delete(id);
    }

    // ==========================================
    // INSTRUCTORS
    // ==========================================

    @Transactional(readOnly = true)
    public List<Instructor> getAllInstructors() {
        return db.instructor().findMany(q -> q.orderBy(f -> f.name().asc()));
    }

    @Transactional(readOnly = true)
    public Optional<Instructor> getInstructorById(int id) {
        return db.instructor().findById(id);
    }

    @Transactional
    public Instructor createInstructor(CreateInstructorRequest req) {
        InstructorCreateInput input = InstructorCreateInput.builder()
                .name(req.name())
                .email(req.email())
                .salary(req.salary())
                .departmentId(req.departmentId())
                .build();
        return db.instructor().create(input);
    }

    @Transactional
    public Instructor updateInstructor(int id, UpdateInstructorRequest req) {
        InstructorUpdateInput input = InstructorUpdateInput.builder()
                .name(req.name())
                .email(req.email())
                .salary(req.salary())
                .departmentId(req.departmentId())
                .build();
        return db.instructor().update(id, input);
    }

    @Transactional
    public boolean deleteInstructor(int id) {
        return db.instructor().delete(id);
    }

    // ==========================================
    // ENROLLMENTS
    // ==========================================

    @Transactional(readOnly = true)
    public List<Enrollment> getAllEnrollments() {
        return db.enrollment().findMany(q -> q.orderBy(f -> f.id().asc()));
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<Enrollment> getEnrollments(
            int page,
            int pageSize,
            Integer studentId,
            Integer courseId,
            String grade,
            String sortBy,
            String sortOrder
    ) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        int offset = (safePage - 1) * safePageSize;

        Consumer<QuerySpec<EnrollmentFilter>> filterConsumer = q -> {
            if (studentId != null) {
                q.where(WherePredicate.eq("student_id", studentId));
            }
            if (courseId != null) {
                q.where(WherePredicate.eq("course_id", courseId));
            }
            if (grade != null && !grade.isBlank() && !"all".equalsIgnoreCase(grade)) {
                q.where(WherePredicate.startsWith("grade", grade.trim()));
            }
        };

        List<Enrollment> items = db.enrollment().findMany(q -> {
            filterConsumer.accept(q);
            boolean isDesc = "desc".equalsIgnoreCase(sortOrder);
            if ("grade".equalsIgnoreCase(sortBy)) {
                q.orderBy(isDesc ? f -> f.grade().desc() : f -> f.grade().asc());
            } else if ("date".equalsIgnoreCase(sortBy)) {
                q.orderBy(isDesc ? f -> f.enrollmentDate().desc() : f -> f.enrollmentDate().asc());
            } else {
                q.orderBy(isDesc ? f -> f.id().desc() : f -> f.id().asc());
            }
            q.limit(safePageSize);
            q.offset(offset);
        });

        long total = db.enrollment().count(filterConsumer);
        return new PaginatedResponse<>(items, total, safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public Optional<Enrollment> getEnrollmentById(int id) {
        return db.enrollment().findById(id);
    }

    @Transactional
    public Enrollment createEnrollment(CreateEnrollmentRequest req) {
        EnrollmentCreateInput input = EnrollmentCreateInput.builder()
                .studentId(req.studentId())
                .courseId(req.courseId())
                .grade(req.grade())
                .gradePoints(req.gradePoints())
                .enrollmentDate(req.enrollmentDate())
                .build();
        return db.enrollment().create(input);
    }

    @Transactional
    public Enrollment updateEnrollment(int id, UpdateEnrollmentRequest req) {
        EnrollmentUpdateInput input = EnrollmentUpdateInput.builder()
                .studentId(req.studentId())
                .courseId(req.courseId())
                .grade(req.grade())
                .gradePoints(req.gradePoints())
                .enrollmentDate(req.enrollmentDate())
                .build();
        return db.enrollment().update(id, input);
    }

    @Transactional
    public boolean deleteEnrollment(int id) {
        return db.enrollment().delete(id);
    }

    // ==========================================
    // VIEW MODELS (READ-ONLY)
    // ==========================================

    /**
     * Database-backed physical view (Stage 1 view model).
     */
    @Transactional(readOnly = true)
    public List<DepartmentStats> getDepartmentStats() {
        return db.departmentStats().findMany(q -> q.orderBy(f -> f.departmentName().asc()));
    }

    /**
     * GraphORM-defined virtual aggregated view (Stage 2 view model).
     */
    @Transactional(readOnly = true)
    public List<StudentDashboard> getStudentDashboards() {
        return db.studentDashboard().findMany(q -> q.orderBy(f -> f.studentName().asc()));
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<StudentDashboard> getStudentDashboards(
            int page,
            int pageSize,
            String search,
            Integer minCourses,
            String sortBy,
            String sortOrder
    ) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        int offset = (safePage - 1) * safePageSize;

        Consumer<QuerySpec<StudentDashboardFilter>> filterConsumer = q -> {
            if (search != null && !search.isBlank()) {
                String term = search.trim();
                q.where(WherePredicate.or(
                        WherePredicate.iContains("studentName", term),
                        WherePredicate.iContains("departmentName", term)
                ));
            }
            if (minCourses != null && minCourses > 0) {
                q.where(WherePredicate.gte("enrolledCourses", minCourses));
            }
        };

        List<StudentDashboard> items = db.studentDashboard().findMany(q -> {
            filterConsumer.accept(q);
            boolean isDesc = "desc".equalsIgnoreCase(sortOrder);
            if ("studentName".equalsIgnoreCase(sortBy)) {
                q.orderBy(isDesc ? f -> f.studentName().desc() : f -> f.studentName().asc());
            } else if ("departmentName".equalsIgnoreCase(sortBy)) {
                q.orderBy(isDesc ? f -> f.departmentName().desc() : f -> f.departmentName().asc());
            } else if ("enrolledCourses".equalsIgnoreCase(sortBy)) {
                q.orderBy(isDesc ? f -> f.enrolledCourses().desc() : f -> f.enrolledCourses().asc());
            } else {
                q.orderBy(isDesc ? f -> f.avgGradePoints().desc() : f -> f.avgGradePoints().asc());
            }
            q.limit(safePageSize);
            q.offset(offset);
        });

        long total = db.studentDashboard().count(filterConsumer);
        return new PaginatedResponse<>(items, total, safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public Optional<StudentDashboard> getStudentDashboard(int studentId) {
        return db.studentDashboard().findFirst(q -> q.where(f -> f.studentId().eq(studentId)));
    }

    @Transactional(readOnly = true)
    public long getDepartmentCount() {
        return db.department().count();
    }

    @Transactional(readOnly = true)
    public long getStudentCount() {
        return db.student().count();
    }

    @Transactional(readOnly = true)
    public long getCourseCount() {
        return db.course().count();
    }

    @Transactional(readOnly = true)
    public long getInstructorCount() {
        return db.instructor().count();
    }

    @Transactional(readOnly = true)
    public long getEnrollmentCount() {
        return db.enrollment().count();
    }

    @Transactional(readOnly = true)
    public long getStudentDashboardCount() {
        return db.studentDashboard().count();
    }
}

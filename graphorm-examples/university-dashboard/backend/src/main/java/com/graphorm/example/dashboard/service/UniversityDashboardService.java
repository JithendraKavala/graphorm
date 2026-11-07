package com.graphorm.example.dashboard.service;

import com.graphorm.example.dashboard.generated.client.GraphOrmClient;
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
import com.graphorm.example.dashboard.web.dto.UpdateCourseRequest;
import com.graphorm.example.dashboard.web.dto.UpdateDepartmentRequest;
import com.graphorm.example.dashboard.web.dto.UpdateEnrollmentRequest;
import com.graphorm.example.dashboard.web.dto.UpdateInstructorRequest;
import com.graphorm.example.dashboard.web.dto.UpdateStudentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service demonstrating GraphORM type-safe client APIs for models and view models
 * supporting the University React Dashboard.
 */
@Service
public class UniversityDashboardService {

    private final GraphOrmClient db;

    public UniversityDashboardService(GraphOrmClient db) {
        this.db = db;
    }

    // ==========================================
    // DEPARTMENTS
    // ==========================================

    @Transactional(readOnly = true)
    public List<Department> getAllDepartments() {
        return db.department().findMany(q -> q.orderBy(f -> f.name().asc()));
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
        StudentUpdateInput input = StudentUpdateInput.builder()
                .name(req.name())
                .email(req.email())
                .gpa(req.gpa())
                .departmentId(req.departmentId())
                .build();
        return db.student().update(id, input);
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
    public Optional<StudentDashboard> getStudentDashboard(int studentId) {
        return db.studentDashboard().findFirst(q -> q.where(f -> f.studentId().eq(studentId)));
    }
}

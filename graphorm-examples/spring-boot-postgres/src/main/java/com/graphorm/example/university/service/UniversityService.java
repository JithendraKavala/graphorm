package com.graphorm.example.university.service;

import com.graphorm.example.university.generated.client.GraphOrmClient;
import com.graphorm.example.university.generated.input.CourseCreateInput;
import com.graphorm.example.university.generated.model.Course;
import com.graphorm.example.university.generated.model.Department;
import com.graphorm.example.university.generated.model.DepartmentStats;
import com.graphorm.example.university.generated.model.Student;
import com.graphorm.example.university.generated.model.StudentDashboard;
import com.graphorm.example.university.web.dto.CreateCourseRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service demonstrating GraphORM type-safe client APIs.
 */
@Service
public class UniversityService {

    private final GraphOrmClient db;

    public UniversityService(GraphOrmClient db) {
        this.db = db;
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
     * Queries the database-backed physical view (Stage 1 view model).
     */
    @Transactional(readOnly = true)
    public List<DepartmentStats> getDepartmentStats() {
        return db.departmentStats().findMany(q -> q.orderBy(f -> f.departmentName().asc()));
    }

    /**
     * Queries the GraphORM-defined virtual aggregated view (Stage 2 view model).
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
}

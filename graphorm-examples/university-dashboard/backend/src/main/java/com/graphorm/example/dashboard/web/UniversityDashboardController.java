package com.graphorm.example.dashboard.web;

import com.graphorm.example.dashboard.generated.model.Course;
import com.graphorm.example.dashboard.generated.model.Department;
import com.graphorm.example.dashboard.generated.model.DepartmentStats;
import com.graphorm.example.dashboard.generated.model.Enrollment;
import com.graphorm.example.dashboard.generated.model.Instructor;
import com.graphorm.example.dashboard.generated.model.Student;
import com.graphorm.example.dashboard.generated.model.StudentDashboard;
import com.graphorm.example.dashboard.service.UniversityDashboardService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller demonstrating client API access to models, database-backed views,
 * and virtual aggregated views for the GraphORM React Dashboard.
 */
@RestController
@RequestMapping("/api/university")
public class UniversityDashboardController {

    private final UniversityDashboardService service;

    public UniversityDashboardController(UniversityDashboardService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public Map<String, Object> getHealth() {
        return Map.of(
                "status", "UP",
                "framework", "GraphORM 0.1.0",
                "dialect", "PostgreSQL",
                "models", List.of("Department", "Instructor", "Student", "Course", "Enrollment"),
                "views", List.of("DepartmentStats", "StudentDashboard"),
                "graphqlEndpoint", "/graphql"
        );
    }

    // ==========================================
    // DEPARTMENTS
    // ==========================================

    @GetMapping("/departments")
    public List<Department> getDepartments() {
        return service.getAllDepartments();
    }

    @GetMapping("/departments/{id}")
    public ResponseEntity<Department> getDepartment(@PathVariable int id) {
        return service.getDepartmentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/departments")
    public ResponseEntity<Department> createDepartment(@RequestBody CreateDepartmentRequest request) {
        Department created = service.createDepartment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/departments/{id}")
    public ResponseEntity<Department> updateDepartment(@PathVariable int id, @RequestBody UpdateDepartmentRequest request) {
        Department updated = service.updateDepartment(id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/departments/{id}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable int id) {
        boolean deleted = service.deleteDepartment(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // ==========================================
    // STUDENTS
    // ==========================================

    @GetMapping("/students")
    public List<Student> getStudents() {
        return service.getAllStudents();
    }

    @GetMapping("/students/{id}")
    public ResponseEntity<Student> getStudent(@PathVariable int id) {
        return service.getStudentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/students")
    public ResponseEntity<Student> createStudent(@RequestBody CreateStudentRequest request) {
        Student created = service.createStudent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/students/{id}")
    public ResponseEntity<Student> updateStudent(@PathVariable int id, @RequestBody UpdateStudentRequest request) {
        Student updated = service.updateStudent(id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/students/{id}")
    public ResponseEntity<Void> deleteStudent(@PathVariable int id) {
        boolean deleted = service.deleteStudent(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // ==========================================
    // COURSES
    // ==========================================

    @GetMapping("/courses")
    public List<Course> getCourses() {
        return service.getAllCourses();
    }

    @GetMapping("/courses/{id}")
    public ResponseEntity<Course> getCourse(@PathVariable int id) {
        return service.getCourseById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/courses")
    public ResponseEntity<Course> createCourse(@RequestBody CreateCourseRequest request) {
        Course created = service.createCourse(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/courses/{id}")
    public ResponseEntity<Course> updateCourse(@PathVariable int id, @RequestBody UpdateCourseRequest request) {
        Course updated = service.updateCourse(id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<Void> deleteCourse(@PathVariable int id) {
        boolean deleted = service.deleteCourse(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // ==========================================
    // INSTRUCTORS
    // ==========================================

    @GetMapping("/instructors")
    public List<Instructor> getInstructors() {
        return service.getAllInstructors();
    }

    @GetMapping("/instructors/{id}")
    public ResponseEntity<Instructor> getInstructor(@PathVariable int id) {
        return service.getInstructorById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/instructors")
    public ResponseEntity<Instructor> createInstructor(@RequestBody CreateInstructorRequest request) {
        Instructor created = service.createInstructor(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/instructors/{id}")
    public ResponseEntity<Instructor> updateInstructor(@PathVariable int id, @RequestBody UpdateInstructorRequest request) {
        Instructor updated = service.updateInstructor(id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/instructors/{id}")
    public ResponseEntity<Void> deleteInstructor(@PathVariable int id) {
        boolean deleted = service.deleteInstructor(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // ==========================================
    // ENROLLMENTS
    // ==========================================

    @GetMapping("/enrollments")
    public List<Enrollment> getEnrollments() {
        return service.getAllEnrollments();
    }

    @GetMapping("/enrollments/{id}")
    public ResponseEntity<Enrollment> getEnrollment(@PathVariable int id) {
        return service.getEnrollmentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/enrollments")
    public ResponseEntity<Enrollment> createEnrollment(@RequestBody CreateEnrollmentRequest request) {
        Enrollment created = service.createEnrollment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/enrollments/{id}")
    public ResponseEntity<Enrollment> updateEnrollment(@PathVariable int id, @RequestBody UpdateEnrollmentRequest request) {
        Enrollment updated = service.updateEnrollment(id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/enrollments/{id}")
    public ResponseEntity<Void> deleteEnrollment(@PathVariable int id) {
        boolean deleted = service.deleteEnrollment(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // ==========================================
    // VIEW MODELS (READ-ONLY)
    // ==========================================

    @GetMapping("/stats")
    public List<DepartmentStats> getDepartmentStats() {
        return service.getDepartmentStats();
    }

    @GetMapping("/dashboards")
    public List<StudentDashboard> getStudentDashboards() {
        return service.getStudentDashboards();
    }

    @GetMapping("/dashboards/{studentId}")
    public ResponseEntity<StudentDashboard> getStudentDashboard(@PathVariable int studentId) {
        return service.getStudentDashboard(studentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

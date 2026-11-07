package com.graphorm.example.university.web;

import com.graphorm.example.university.generated.model.Course;
import com.graphorm.example.university.generated.model.Department;
import com.graphorm.example.university.generated.model.DepartmentStats;
import com.graphorm.example.university.generated.model.Student;
import com.graphorm.example.university.generated.model.StudentDashboard;
import com.graphorm.example.university.service.UniversityService;
import com.graphorm.example.university.web.dto.CreateCourseRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller demonstrating client API access to models, database-backed views,
 * and virtual aggregated views.
 */
@RestController
@RequestMapping("/api/university")
public class UniversityController {

    private final UniversityService service;

    public UniversityController(UniversityService service) {
        this.service = service;
    }

    @GetMapping("/departments")
    public List<Department> getDepartments() {
        return service.getAllDepartments();
    }

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

    @GetMapping("/courses")
    public List<Course> getCourses() {
        return service.getAllCourses();
    }

    @PostMapping("/courses")
    public ResponseEntity<Course> createCourse(@RequestBody CreateCourseRequest request) {
        Course created = service.createCourse(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Database-backed physical view (Stage 1 view model).
     */
    @GetMapping("/stats")
    public List<DepartmentStats> getDepartmentStats() {
        return service.getDepartmentStats();
    }

    /**
     * Virtual aggregated view (Stage 2 view model).
     */
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

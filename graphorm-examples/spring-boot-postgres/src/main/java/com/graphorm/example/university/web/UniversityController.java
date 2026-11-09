package com.graphorm.example.university.web;

import com.graphorm.core.exception.DuplicateKeyException;
import com.graphorm.core.exception.OptimisticLockException;
import com.graphorm.example.university.generated.model.AuditLog;
import com.graphorm.example.university.generated.model.AuditSummary;
import com.graphorm.example.university.generated.model.Course;
import com.graphorm.example.university.generated.model.Department;
import com.graphorm.example.university.generated.model.DepartmentStats;
import com.graphorm.example.university.generated.model.Student;
import com.graphorm.example.university.generated.model.StudentDashboard;
import com.graphorm.example.university.service.UniversityService;
import com.graphorm.example.university.web.dto.CreateCourseRequest;
import com.graphorm.example.university.web.dto.UpdateStudentOptimisticRequest;
import com.graphorm.example.university.web.dto.UpsertStudentRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller demonstrating client API access to models, database-backed views,
 * virtual aggregated views, upsert, batch operations, and optimistic locking.
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

    @PostMapping("/students/upsert")
    public ResponseEntity<Student> upsertStudent(@RequestBody UpsertStudentRequest request) {
        Student student = service.upsertStudent(request);
        return ResponseEntity.ok(student);
    }

    @PutMapping("/students/{id}/optimistic")
    public ResponseEntity<Student> updateStudentOptimistic(
            @PathVariable int id,
            @RequestBody UpdateStudentOptimisticRequest request
    ) {
        Student updated = service.updateStudentOptimistic(id, request);
        return ResponseEntity.ok(updated);
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

    @PostMapping("/courses/batch")
    public ResponseEntity<List<Course>> batchCreateCourses(@RequestBody List<CreateCourseRequest> requests) {
        List<Course> created = service.batchCreateCourses(requests);
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

    /**
     * Multi-tenant audit logs endpoint.
     */
    @GetMapping("/audit-logs")
    public List<AuditLog> getAuditLogs(
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "tenant-main") String tenantId
    ) {
        return service.getAuditLogsForTenant(tenantId);
    }

    @GetMapping("/audit-summaries")
    public List<AuditSummary> getAuditSummaries() {
        return service.getAuditSummaries();
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "OPTIMISTIC_LOCK_FAILURE",
                "message", ex.getMessage(),
                "entityName", ex.entityName() != null ? ex.entityName() : "unknown",
                "expectedVersion", ex.expectedVersion() != null ? ex.expectedVersion() : -1
        ));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateKey(DuplicateKeyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "DUPLICATE_KEY",
                "message", ex.getMessage()
        ));
    }
}

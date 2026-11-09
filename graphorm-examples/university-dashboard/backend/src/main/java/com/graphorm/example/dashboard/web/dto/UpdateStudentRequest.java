package com.graphorm.example.dashboard.web.dto;

/**
 * Request payload for updating a student, supporting optimistic locking via version.
 */
public record UpdateStudentRequest(
        String name,
        String email,
        Double gpa,
        Integer departmentId,
        Integer version
) {
    public UpdateStudentRequest(String name, String email, Double gpa, Integer departmentId) {
        this(name, email, gpa, departmentId, null);
    }
}

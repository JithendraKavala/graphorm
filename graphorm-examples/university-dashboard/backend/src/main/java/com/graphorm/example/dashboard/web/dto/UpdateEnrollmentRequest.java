package com.graphorm.example.dashboard.web.dto;

public record UpdateEnrollmentRequest(
        Integer studentId,
        Integer courseId,
        String grade,
        Double gradePoints,
        String enrollmentDate
) {}

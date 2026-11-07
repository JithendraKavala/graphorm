package com.graphorm.example.dashboard.web.dto;

public record CreateEnrollmentRequest(
        Integer studentId,
        Integer courseId,
        String grade,
        Double gradePoints,
        String enrollmentDate
) {}

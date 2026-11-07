package com.graphorm.example.dashboard.web.dto;

public record UpdateStudentRequest(
        String name,
        String email,
        Double gpa,
        Integer departmentId
) {}

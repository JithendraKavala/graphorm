package com.graphorm.example.dashboard.web.dto;

public record CreateInstructorRequest(
        String name,
        String email,
        Double salary,
        Integer departmentId
) {}

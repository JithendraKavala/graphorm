package com.graphorm.example.dashboard.web.dto;

public record UpdateInstructorRequest(
        String name,
        String email,
        Double salary,
        Integer departmentId
) {}

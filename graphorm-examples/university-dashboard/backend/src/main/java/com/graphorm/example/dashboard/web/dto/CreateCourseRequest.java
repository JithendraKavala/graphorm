package com.graphorm.example.dashboard.web.dto;

public record CreateCourseRequest(
        String title,
        String code,
        Integer credits,
        Integer departmentId,
        Integer instructorId
) {}

package com.graphorm.example.university.web.dto;

/**
 * Request payload for creating a new course via the University REST API.
 */
public record CreateCourseRequest(
        String title,
        String code,
        Integer credits,
        Integer departmentId,
        Integer instructorId
) {}

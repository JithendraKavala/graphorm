package com.graphorm.example.university.web.dto;

/**
 * Request payload for optimistic lock student updates.
 */
public record UpdateStudentOptimisticRequest(
        String name,
        Double gpa,
        Integer expectedVersion
) {}

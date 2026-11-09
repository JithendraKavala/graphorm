package com.graphorm.example.dashboard.web.dto;

/**
 * Request payload for upserting a student (insert or update on unique email conflict).
 */
public record UpsertStudentRequest(
        String name,
        String email,
        Double gpa,
        Integer departmentId
) {}

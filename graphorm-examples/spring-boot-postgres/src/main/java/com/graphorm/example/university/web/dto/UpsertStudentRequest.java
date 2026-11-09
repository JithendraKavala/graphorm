package com.graphorm.example.university.web.dto;

/**
 * Request payload for upserting a student (insert or update on email conflict).
 */
public record UpsertStudentRequest(
        String name,
        String email,
        Double gpa,
        Integer departmentId
) {}

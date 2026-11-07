package com.graphorm.example.dashboard.web.dto;

public record CreateDepartmentRequest(
        String name,
        String code
) {}

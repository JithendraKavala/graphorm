package com.graphorm.example.university.generated.model;

/**
 * Read-only projection record representing the {@code DepartmentStats} view.
 * <p>
 * STRICT READ-ONLY INVARIANT: View models are strictly immutable projections
 * with zero mutation or setter methods.
 */
public record DepartmentStats(
        java.lang.Integer id,
        java.lang.String departmentName,
        java.lang.Integer studentCount,
        java.lang.Integer courseCount
) {
    public static final String VIEW_NAME = "DepartmentStats";

    public static final boolean IS_DATABASE_BACKED = true;
}

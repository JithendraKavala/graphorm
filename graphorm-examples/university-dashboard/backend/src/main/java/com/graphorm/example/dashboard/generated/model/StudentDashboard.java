package com.graphorm.example.dashboard.generated.model;

/**
 * Read-only projection record representing the {@code StudentDashboard} view.
 * <p>
 * STRICT READ-ONLY INVARIANT: View models are strictly immutable projections
 * with zero mutation or setter methods.
 */
public record StudentDashboard(
        java.lang.Integer studentId,
        java.lang.String studentName,
        java.lang.String departmentName,
        java.lang.Integer enrolledCourses,
        java.lang.Double avgGradePoints
) {
    public static final String VIEW_NAME = "StudentDashboard";

    public static final boolean IS_DATABASE_BACKED = false;
}

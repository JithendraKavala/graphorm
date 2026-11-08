package com.graphorm.example.university.generated.model;

/**
 * Read-only projection record representing the {@code AuditSummary} view.
 * <p>
 * STRICT READ-ONLY INVARIANT: View models are strictly immutable projections
 * with zero mutation or setter methods.
 */
public record AuditSummary(
        java.lang.Integer id,
        java.lang.String action,
        java.lang.String tenantId,
        java.lang.String performedBy
) {
    public static final String VIEW_NAME = "AuditSummary";

    public static final boolean IS_DATABASE_BACKED = false;
}

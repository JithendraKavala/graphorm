package com.graphorm.example.university.generated.model;

/**
 * Immutable Java 21 model record representing the {@code AuditLog} entity (table {@code audit_logs}).
 */
public record AuditLog(
        java.lang.Integer id,
        java.lang.String action,
        java.lang.String details,
        java.lang.String tenantId,
        java.lang.String performedBy
) {
    public static final String MODEL_NAME = "AuditLog";

    public static final String TABLE_NAME = "audit_logs";

    /**
     * Creates a new {@link Builder} for {@link AuditLog}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a copy of this {@link AuditLog} with the {@code id} component updated.
     */
    public AuditLog withId(Integer id) {
        return new AuditLog(id, this.action, this.details, this.tenantId, this.performedBy);
    }

    /**
     * Returns a copy of this {@link AuditLog} with the {@code action} component updated.
     */
    public AuditLog withAction(String action) {
        return new AuditLog(this.id, action, this.details, this.tenantId, this.performedBy);
    }

    /**
     * Returns a copy of this {@link AuditLog} with the {@code details} component updated.
     */
    public AuditLog withDetails(String details) {
        return new AuditLog(this.id, this.action, details, this.tenantId, this.performedBy);
    }

    /**
     * Returns a copy of this {@link AuditLog} with the {@code tenantId} component updated.
     */
    public AuditLog withTenantId(String tenantId) {
        return new AuditLog(this.id, this.action, this.details, tenantId, this.performedBy);
    }

    /**
     * Returns a copy of this {@link AuditLog} with the {@code performedBy} component updated.
     */
    public AuditLog withPerformedBy(String performedBy) {
        return new AuditLog(this.id, this.action, this.details, this.tenantId, performedBy);
    }

    /**
     * Fluent builder for constructing {@link AuditLog} instances.
     */
    public static final class Builder {
        private Integer id;

        private String action;

        private String details;

        private String tenantId;

        private String performedBy;

        public Builder id(Integer id) {
            this.id = id;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder details(String details) {
            this.details = details;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder performedBy(String performedBy) {
            this.performedBy = performedBy;
            return this;
        }

        public AuditLog build() {
            return new AuditLog(id, action, details, tenantId, performedBy);
        }
    }
}

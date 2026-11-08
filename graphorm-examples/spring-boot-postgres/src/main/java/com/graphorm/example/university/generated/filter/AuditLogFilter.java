package com.graphorm.example.university.generated.filter;

import com.graphorm.core.query.WherePredicate;
import com.graphorm.generator.filter.ComparableCriteriaBuilder;
import com.graphorm.generator.filter.FilterBuilder;
import com.graphorm.generator.filter.IntFieldFilter;
import com.graphorm.generator.filter.StringCriteriaBuilder;
import com.graphorm.generator.filter.StringFieldFilter;

/**
 * Type-safe criteria and filter builder for {@code AuditLog}.
 */
public class AuditLogFilter {
    public static final AuditLogFilter INSTANCE = new AuditLogFilter();

    public static AuditLogFilter create() {
        return INSTANCE;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Criteria filter for field {@code id} (column {@code id}).
     */
    public IntFieldFilter id() {
        return new IntFieldFilter("id");
    }

    /**
     * Criteria filter for field {@code action} (column {@code action}).
     */
    public StringFieldFilter action() {
        return new StringFieldFilter("action");
    }

    /**
     * Criteria filter for field {@code details} (column {@code details}).
     */
    public StringFieldFilter details() {
        return new StringFieldFilter("details");
    }

    /**
     * Criteria filter for field {@code tenantId} (column {@code tenant_id}).
     */
    public StringFieldFilter tenantId() {
        return new StringFieldFilter("tenant_id");
    }

    /**
     * Criteria filter for field {@code performedBy} (column {@code performed_by}).
     */
    public StringFieldFilter performedBy() {
        return new StringFieldFilter("performed_by");
    }

    /**
     * Fluent criteria builder for {@link AuditLogFilter}.
     */
    public static final class Builder implements FilterBuilder<Builder> {
        private WherePredicate predicate;

        @Override
        public Builder addPredicate(WherePredicate predicate) {
            if (predicate != null) this.predicate = this.predicate == null ? predicate : this.predicate.and(predicate);
            return this;
        }

        @Override
        public WherePredicate build() {
            return this.predicate;
        }

        public ComparableCriteriaBuilder<Builder, Integer> id() {
            return new ComparableCriteriaBuilder<Builder, Integer>(this, "id");
        }

        public StringCriteriaBuilder<Builder> action() {
            return new StringCriteriaBuilder<Builder>(this, "action");
        }

        public StringCriteriaBuilder<Builder> details() {
            return new StringCriteriaBuilder<Builder>(this, "details");
        }

        public StringCriteriaBuilder<Builder> tenantId() {
            return new StringCriteriaBuilder<Builder>(this, "tenant_id");
        }

        public StringCriteriaBuilder<Builder> performedBy() {
            return new StringCriteriaBuilder<Builder>(this, "performed_by");
        }
    }
}

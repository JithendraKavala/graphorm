package com.graphorm.example.university.generated.filter;

import com.graphorm.core.query.WherePredicate;
import com.graphorm.generator.filter.ComparableCriteriaBuilder;
import com.graphorm.generator.filter.FilterBuilder;
import com.graphorm.generator.filter.IntFieldFilter;
import com.graphorm.generator.filter.StringCriteriaBuilder;
import com.graphorm.generator.filter.StringFieldFilter;

/**
 * Type-safe criteria and filter builder for {@code AuditSummary}.
 */
public class AuditSummaryFilter {
    public static final AuditSummaryFilter INSTANCE = new AuditSummaryFilter();

    public static AuditSummaryFilter create() {
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
     * Criteria filter for field {@code tenantId} (column {@code tenantId}).
     */
    public StringFieldFilter tenantId() {
        return new StringFieldFilter("tenantId");
    }

    /**
     * Criteria filter for field {@code performedBy} (column {@code performedBy}).
     */
    public StringFieldFilter performedBy() {
        return new StringFieldFilter("performedBy");
    }

    /**
     * Fluent criteria builder for {@link AuditSummaryFilter}.
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

        public StringCriteriaBuilder<Builder> tenantId() {
            return new StringCriteriaBuilder<Builder>(this, "tenantId");
        }

        public StringCriteriaBuilder<Builder> performedBy() {
            return new StringCriteriaBuilder<Builder>(this, "performedBy");
        }
    }
}

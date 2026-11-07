package com.graphorm.example.dashboard.generated.filter;

import com.graphorm.core.query.WherePredicate;
import com.graphorm.generator.filter.ComparableCriteriaBuilder;
import com.graphorm.generator.filter.FilterBuilder;
import com.graphorm.generator.filter.IntFieldFilter;
import com.graphorm.generator.filter.StringCriteriaBuilder;
import com.graphorm.generator.filter.StringFieldFilter;

/**
 * Type-safe criteria and filter builder for {@code DepartmentStats}.
 */
public class DepartmentStatsFilter {
    public static final DepartmentStatsFilter INSTANCE = new DepartmentStatsFilter();

    public static DepartmentStatsFilter create() {
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
     * Criteria filter for field {@code departmentName} (column {@code departmentName}).
     */
    public StringFieldFilter departmentName() {
        return new StringFieldFilter("departmentName");
    }

    /**
     * Criteria filter for field {@code studentCount} (column {@code studentCount}).
     */
    public IntFieldFilter studentCount() {
        return new IntFieldFilter("studentCount");
    }

    /**
     * Criteria filter for field {@code courseCount} (column {@code courseCount}).
     */
    public IntFieldFilter courseCount() {
        return new IntFieldFilter("courseCount");
    }

    /**
     * Fluent criteria builder for {@link DepartmentStatsFilter}.
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

        public StringCriteriaBuilder<Builder> departmentName() {
            return new StringCriteriaBuilder<Builder>(this, "departmentName");
        }

        public ComparableCriteriaBuilder<Builder, Integer> studentCount() {
            return new ComparableCriteriaBuilder<Builder, Integer>(this, "studentCount");
        }

        public ComparableCriteriaBuilder<Builder, Integer> courseCount() {
            return new ComparableCriteriaBuilder<Builder, Integer>(this, "courseCount");
        }
    }
}

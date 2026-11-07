package com.graphorm.example.dashboard.generated.filter;

import com.graphorm.core.query.WherePredicate;
import com.graphorm.generator.filter.ComparableCriteriaBuilder;
import com.graphorm.generator.filter.FilterBuilder;
import com.graphorm.generator.filter.IntFieldFilter;
import com.graphorm.generator.filter.StringCriteriaBuilder;
import com.graphorm.generator.filter.StringFieldFilter;

/**
 * Type-safe criteria and filter builder for {@code Department}.
 */
public class DepartmentFilter {
    public static final DepartmentFilter INSTANCE = new DepartmentFilter();

    public static DepartmentFilter create() {
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
     * Criteria filter for field {@code name} (column {@code dept_name}).
     */
    public StringFieldFilter name() {
        return new StringFieldFilter("dept_name");
    }

    /**
     * Criteria filter for field {@code code} (column {@code code}).
     */
    public StringFieldFilter code() {
        return new StringFieldFilter("code");
    }

    /**
     * Fluent criteria builder for {@link DepartmentFilter}.
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

        public StringCriteriaBuilder<Builder> name() {
            return new StringCriteriaBuilder<Builder>(this, "dept_name");
        }

        public StringCriteriaBuilder<Builder> code() {
            return new StringCriteriaBuilder<Builder>(this, "code");
        }
    }
}

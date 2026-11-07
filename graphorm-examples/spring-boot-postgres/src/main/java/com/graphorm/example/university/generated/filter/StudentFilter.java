package com.graphorm.example.university.generated.filter;

import com.graphorm.core.query.WherePredicate;
import com.graphorm.generator.filter.ComparableCriteriaBuilder;
import com.graphorm.generator.filter.DoubleFieldFilter;
import com.graphorm.generator.filter.FilterBuilder;
import com.graphorm.generator.filter.IntFieldFilter;
import com.graphorm.generator.filter.StringCriteriaBuilder;
import com.graphorm.generator.filter.StringFieldFilter;

/**
 * Type-safe criteria and filter builder for {@code Student}.
 */
public class StudentFilter {
    public static final StudentFilter INSTANCE = new StudentFilter();

    public static StudentFilter create() {
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
     * Criteria filter for field {@code name} (column {@code name}).
     */
    public StringFieldFilter name() {
        return new StringFieldFilter("name");
    }

    /**
     * Criteria filter for field {@code email} (column {@code email}).
     */
    public StringFieldFilter email() {
        return new StringFieldFilter("email");
    }

    /**
     * Criteria filter for field {@code gpa} (column {@code gpa}).
     */
    public DoubleFieldFilter gpa() {
        return new DoubleFieldFilter("gpa");
    }

    /**
     * Criteria filter for field {@code departmentId} (column {@code department_id}).
     */
    public IntFieldFilter departmentId() {
        return new IntFieldFilter("department_id");
    }

    /**
     * Fluent criteria builder for {@link StudentFilter}.
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
            return new StringCriteriaBuilder<Builder>(this, "name");
        }

        public StringCriteriaBuilder<Builder> email() {
            return new StringCriteriaBuilder<Builder>(this, "email");
        }

        public ComparableCriteriaBuilder<Builder, Double> gpa() {
            return new ComparableCriteriaBuilder<Builder, Double>(this, "gpa");
        }

        public ComparableCriteriaBuilder<Builder, Integer> departmentId() {
            return new ComparableCriteriaBuilder<Builder, Integer>(this, "department_id");
        }
    }
}

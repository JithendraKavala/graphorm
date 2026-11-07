package com.graphorm.example.university.generated.filter;

import com.graphorm.core.query.WherePredicate;
import com.graphorm.generator.filter.ComparableCriteriaBuilder;
import com.graphorm.generator.filter.FilterBuilder;
import com.graphorm.generator.filter.IntFieldFilter;
import com.graphorm.generator.filter.StringCriteriaBuilder;
import com.graphorm.generator.filter.StringFieldFilter;

/**
 * Type-safe criteria and filter builder for {@code Course}.
 */
public class CourseFilter {
    public static final CourseFilter INSTANCE = new CourseFilter();

    public static CourseFilter create() {
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
     * Criteria filter for field {@code title} (column {@code title}).
     */
    public StringFieldFilter title() {
        return new StringFieldFilter("title");
    }

    /**
     * Criteria filter for field {@code code} (column {@code code}).
     */
    public StringFieldFilter code() {
        return new StringFieldFilter("code");
    }

    /**
     * Criteria filter for field {@code credits} (column {@code credits}).
     */
    public IntFieldFilter credits() {
        return new IntFieldFilter("credits");
    }

    /**
     * Criteria filter for field {@code departmentId} (column {@code department_id}).
     */
    public IntFieldFilter departmentId() {
        return new IntFieldFilter("department_id");
    }

    /**
     * Criteria filter for field {@code instructorId} (column {@code instructor_id}).
     */
    public IntFieldFilter instructorId() {
        return new IntFieldFilter("instructor_id");
    }

    /**
     * Fluent criteria builder for {@link CourseFilter}.
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

        public StringCriteriaBuilder<Builder> title() {
            return new StringCriteriaBuilder<Builder>(this, "title");
        }

        public StringCriteriaBuilder<Builder> code() {
            return new StringCriteriaBuilder<Builder>(this, "code");
        }

        public ComparableCriteriaBuilder<Builder, Integer> credits() {
            return new ComparableCriteriaBuilder<Builder, Integer>(this, "credits");
        }

        public ComparableCriteriaBuilder<Builder, Integer> departmentId() {
            return new ComparableCriteriaBuilder<Builder, Integer>(this, "department_id");
        }

        public ComparableCriteriaBuilder<Builder, Integer> instructorId() {
            return new ComparableCriteriaBuilder<Builder, Integer>(this, "instructor_id");
        }
    }
}

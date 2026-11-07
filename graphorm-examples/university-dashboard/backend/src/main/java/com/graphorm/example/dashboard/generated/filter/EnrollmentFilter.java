package com.graphorm.example.dashboard.generated.filter;

import com.graphorm.core.query.WherePredicate;
import com.graphorm.generator.filter.ComparableCriteriaBuilder;
import com.graphorm.generator.filter.DoubleFieldFilter;
import com.graphorm.generator.filter.FilterBuilder;
import com.graphorm.generator.filter.IntFieldFilter;
import com.graphorm.generator.filter.StringCriteriaBuilder;
import com.graphorm.generator.filter.StringFieldFilter;

/**
 * Type-safe criteria and filter builder for {@code Enrollment}.
 */
public class EnrollmentFilter {
    public static final EnrollmentFilter INSTANCE = new EnrollmentFilter();

    public static EnrollmentFilter create() {
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
     * Criteria filter for field {@code studentId} (column {@code student_id}).
     */
    public IntFieldFilter studentId() {
        return new IntFieldFilter("student_id");
    }

    /**
     * Criteria filter for field {@code courseId} (column {@code course_id}).
     */
    public IntFieldFilter courseId() {
        return new IntFieldFilter("course_id");
    }

    /**
     * Criteria filter for field {@code grade} (column {@code grade}).
     */
    public StringFieldFilter grade() {
        return new StringFieldFilter("grade");
    }

    /**
     * Criteria filter for field {@code gradePoints} (column {@code grade_points}).
     */
    public DoubleFieldFilter gradePoints() {
        return new DoubleFieldFilter("grade_points");
    }

    /**
     * Criteria filter for field {@code enrollmentDate} (column {@code enrollment_date}).
     */
    public StringFieldFilter enrollmentDate() {
        return new StringFieldFilter("enrollment_date");
    }

    /**
     * Fluent criteria builder for {@link EnrollmentFilter}.
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

        public ComparableCriteriaBuilder<Builder, Integer> studentId() {
            return new ComparableCriteriaBuilder<Builder, Integer>(this, "student_id");
        }

        public ComparableCriteriaBuilder<Builder, Integer> courseId() {
            return new ComparableCriteriaBuilder<Builder, Integer>(this, "course_id");
        }

        public StringCriteriaBuilder<Builder> grade() {
            return new StringCriteriaBuilder<Builder>(this, "grade");
        }

        public ComparableCriteriaBuilder<Builder, Double> gradePoints() {
            return new ComparableCriteriaBuilder<Builder, Double>(this, "grade_points");
        }

        public StringCriteriaBuilder<Builder> enrollmentDate() {
            return new StringCriteriaBuilder<Builder>(this, "enrollment_date");
        }
    }
}

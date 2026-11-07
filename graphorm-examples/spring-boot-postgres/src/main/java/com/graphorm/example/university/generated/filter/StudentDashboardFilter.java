package com.graphorm.example.university.generated.filter;

import com.graphorm.core.query.WherePredicate;
import com.graphorm.generator.filter.ComparableCriteriaBuilder;
import com.graphorm.generator.filter.DoubleFieldFilter;
import com.graphorm.generator.filter.FilterBuilder;
import com.graphorm.generator.filter.IntFieldFilter;
import com.graphorm.generator.filter.StringCriteriaBuilder;
import com.graphorm.generator.filter.StringFieldFilter;

/**
 * Type-safe criteria and filter builder for {@code StudentDashboard}.
 */
public class StudentDashboardFilter {
    public static final StudentDashboardFilter INSTANCE = new StudentDashboardFilter();

    public static StudentDashboardFilter create() {
        return INSTANCE;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Criteria filter for field {@code studentId} (column {@code studentId}).
     */
    public IntFieldFilter studentId() {
        return new IntFieldFilter("studentId");
    }

    /**
     * Criteria filter for field {@code studentName} (column {@code studentName}).
     */
    public StringFieldFilter studentName() {
        return new StringFieldFilter("studentName");
    }

    /**
     * Criteria filter for field {@code departmentName} (column {@code departmentName}).
     */
    public StringFieldFilter departmentName() {
        return new StringFieldFilter("departmentName");
    }

    /**
     * Criteria filter for field {@code enrolledCourses} (column {@code enrolledCourses}).
     */
    public IntFieldFilter enrolledCourses() {
        return new IntFieldFilter("enrolledCourses");
    }

    /**
     * Criteria filter for field {@code avgGradePoints} (column {@code avgGradePoints}).
     */
    public DoubleFieldFilter avgGradePoints() {
        return new DoubleFieldFilter("avgGradePoints");
    }

    /**
     * Fluent criteria builder for {@link StudentDashboardFilter}.
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

        public ComparableCriteriaBuilder<Builder, Integer> studentId() {
            return new ComparableCriteriaBuilder<Builder, Integer>(this, "studentId");
        }

        public StringCriteriaBuilder<Builder> studentName() {
            return new StringCriteriaBuilder<Builder>(this, "studentName");
        }

        public StringCriteriaBuilder<Builder> departmentName() {
            return new StringCriteriaBuilder<Builder>(this, "departmentName");
        }

        public ComparableCriteriaBuilder<Builder, Integer> enrolledCourses() {
            return new ComparableCriteriaBuilder<Builder, Integer>(this, "enrolledCourses");
        }

        public ComparableCriteriaBuilder<Builder, Double> avgGradePoints() {
            return new ComparableCriteriaBuilder<Builder, Double>(this, "avgGradePoints");
        }
    }
}

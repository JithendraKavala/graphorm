package com.graphorm.example.university.generated.filter;

import com.graphorm.core.query.WherePredicate;
import com.graphorm.generator.filter.ComparableCriteriaBuilder;
import com.graphorm.generator.filter.DoubleFieldFilter;
import com.graphorm.generator.filter.FilterBuilder;
import com.graphorm.generator.filter.IntFieldFilter;
import com.graphorm.generator.filter.StringCriteriaBuilder;
import com.graphorm.generator.filter.StringFieldFilter;

/**
 * Type-safe criteria and filter builder for {@code GradeScale}.
 */
public class GradeScaleFilter {
    public static final GradeScaleFilter INSTANCE = new GradeScaleFilter();

    public static GradeScaleFilter create() {
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
     * Criteria filter for field {@code letter} (column {@code letter}).
     */
    public StringFieldFilter letter() {
        return new StringFieldFilter("letter");
    }

    /**
     * Criteria filter for field {@code minScore} (column {@code min_score}).
     */
    public DoubleFieldFilter minScore() {
        return new DoubleFieldFilter("min_score");
    }

    /**
     * Criteria filter for field {@code gpaPoint} (column {@code gpa_point}).
     */
    public DoubleFieldFilter gpaPoint() {
        return new DoubleFieldFilter("gpa_point");
    }

    /**
     * Fluent criteria builder for {@link GradeScaleFilter}.
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

        public StringCriteriaBuilder<Builder> letter() {
            return new StringCriteriaBuilder<Builder>(this, "letter");
        }

        public ComparableCriteriaBuilder<Builder, Double> minScore() {
            return new ComparableCriteriaBuilder<Builder, Double>(this, "min_score");
        }

        public ComparableCriteriaBuilder<Builder, Double> gpaPoint() {
            return new ComparableCriteriaBuilder<Builder, Double>(this, "gpa_point");
        }
    }
}

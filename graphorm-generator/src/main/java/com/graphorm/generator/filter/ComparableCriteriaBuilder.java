package com.graphorm.generator.filter;

import com.graphorm.core.query.WherePredicate;

import java.util.Objects;

/**
 * Fluent criteria builder for comparable fields.
 *
 * @param <B> parent filter builder type
 * @param <T> comparable value type
 */
public class ComparableCriteriaBuilder<B extends FilterBuilder<B>, T extends Comparable<T>>
        extends CriteriaBuilder<B, T> {

    public ComparableCriteriaBuilder(B parent, String column) {
        super(parent, column);
    }

    public B gt(T value) {
        Objects.requireNonNull(value, "Value for gt must not be null");
        return parent.addPredicate(WherePredicate.gt(column, value));
    }

    public B gte(T value) {
        Objects.requireNonNull(value, "Value for gte must not be null");
        return parent.addPredicate(WherePredicate.gte(column, value));
    }

    public B lt(T value) {
        Objects.requireNonNull(value, "Value for lt must not be null");
        return parent.addPredicate(WherePredicate.lt(column, value));
    }

    public B lte(T value) {
        Objects.requireNonNull(value, "Value for lte must not be null");
        return parent.addPredicate(WherePredicate.lte(column, value));
    }

    public B between(T lower, T upper) {
        Objects.requireNonNull(lower, "Lower bound must not be null");
        Objects.requireNonNull(upper, "Upper bound must not be null");
        return parent.addPredicate(WherePredicate.between(column, lower, upper));
    }
}

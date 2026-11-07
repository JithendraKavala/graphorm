package com.graphorm.generator.filter;

import com.graphorm.core.query.WherePredicate;

import java.util.Objects;

/**
 * Filter for fields with comparable values (numbers, dates, strings), supporting
 * relational operators and ranges.
 *
 * @param <T> the comparable field type
 */
public class ComparableFieldFilter<T extends Comparable<T>> extends FieldFilter<T> {

    public ComparableFieldFilter(String column) {
        super(column);
    }

    public WherePredicate gt(T value) {
        Objects.requireNonNull(value, "Value for gt must not be null");
        return WherePredicate.gt(column, value);
    }

    public WherePredicate gte(T value) {
        Objects.requireNonNull(value, "Value for gte must not be null");
        return WherePredicate.gte(column, value);
    }

    public WherePredicate lt(T value) {
        Objects.requireNonNull(value, "Value for lt must not be null");
        return WherePredicate.lt(column, value);
    }

    public WherePredicate lte(T value) {
        Objects.requireNonNull(value, "Value for lte must not be null");
        return WherePredicate.lte(column, value);
    }

    public WherePredicate between(T lower, T upper) {
        Objects.requireNonNull(lower, "Lower bound for between must not be null");
        Objects.requireNonNull(upper, "Upper bound for between must not be null");
        return WherePredicate.between(column, lower, upper);
    }
}

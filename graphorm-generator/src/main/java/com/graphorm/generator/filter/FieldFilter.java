package com.graphorm.generator.filter;

import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.WherePredicate;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * Base type-safe field filter providing equality, nullability, collection containment,
 * and ordering expressions.
 *
 * @param <T> the field value type
 */
public class FieldFilter<T> {

    protected final String column;

    public FieldFilter(String column) {
        this.column = Objects.requireNonNull(column, "Column must not be null");
    }

    public String column() {
        return column;
    }

    public WherePredicate eq(T value) {
        if (value == null) {
            return isNull();
        }
        return WherePredicate.eq(column, value);
    }

    public WherePredicate neq(T value) {
        if (value == null) {
            return isNotNull();
        }
        return WherePredicate.neq(column, value);
    }

    public WherePredicate isNull() {
        return WherePredicate.isNull(column);
    }

    public WherePredicate isNotNull() {
        return WherePredicate.isNotNull(column);
    }

    public WherePredicate in(Collection<T> values) {
        Objects.requireNonNull(values, "Values collection must not be null");
        return WherePredicate.in(column, values);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final WherePredicate in(T... values) {
        Objects.requireNonNull(values, "Values array must not be null");
        return in(Arrays.asList(values));
    }

    public WherePredicate notIn(Collection<T> values) {
        Objects.requireNonNull(values, "Values collection must not be null");
        return WherePredicate.notIn(column, values);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final WherePredicate notIn(T... values) {
        Objects.requireNonNull(values, "Values array must not be null");
        return notIn(Arrays.asList(values));
    }

    public OrderByClause asc() {
        return OrderByClause.asc(column);
    }

    public OrderByClause desc() {
        return OrderByClause.desc(column);
    }
}

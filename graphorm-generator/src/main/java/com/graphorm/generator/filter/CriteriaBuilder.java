package com.graphorm.generator.filter;

import com.graphorm.core.query.WherePredicate;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * Fluent criteria builder for a single field in a filter builder.
 *
 * @param <B> the parent filter builder type
 * @param <T> the field value type
 */
public class CriteriaBuilder<B extends FilterBuilder<B>, T> {

    protected final B parent;
    protected final String column;

    public CriteriaBuilder(B parent, String column) {
        this.parent = Objects.requireNonNull(parent, "Parent filter builder must not be null");
        this.column = Objects.requireNonNull(column, "Column must not be null");
    }

    public B eq(T value) {
        if (value == null) {
            return isNull();
        }
        return parent.addPredicate(WherePredicate.eq(column, value));
    }

    public B neq(T value) {
        if (value == null) {
            return isNotNull();
        }
        return parent.addPredicate(WherePredicate.neq(column, value));
    }

    public B isNull() {
        return parent.addPredicate(WherePredicate.isNull(column));
    }

    public B isNotNull() {
        return parent.addPredicate(WherePredicate.isNotNull(column));
    }

    public B in(Collection<T> values) {
        Objects.requireNonNull(values, "Values must not be null");
        return parent.addPredicate(WherePredicate.in(column, values));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final B in(T... values) {
        Objects.requireNonNull(values, "Values must not be null");
        return in(Arrays.asList(values));
    }

    public B notIn(Collection<T> values) {
        Objects.requireNonNull(values, "Values must not be null");
        return parent.addPredicate(WherePredicate.notIn(column, values));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final B notIn(T... values) {
        Objects.requireNonNull(values, "Values must not be null");
        return notIn(Arrays.asList(values));
    }
}

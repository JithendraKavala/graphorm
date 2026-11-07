package com.graphorm.generator.filter;

import com.graphorm.core.query.WherePredicate;

import java.util.Objects;

/**
 * Fluent criteria builder for String fields.
 *
 * @param <B> parent filter builder type
 */
public class StringCriteriaBuilder<B extends FilterBuilder<B>>
        extends ComparableCriteriaBuilder<B, String> {

    public StringCriteriaBuilder(B parent, String column) {
        super(parent, column);
    }

    public B contains(String substring) {
        Objects.requireNonNull(substring, "Substring must not be null");
        return parent.addPredicate(WherePredicate.contains(column, substring));
    }

    public B startsWith(String prefix) {
        Objects.requireNonNull(prefix, "Prefix must not be null");
        return parent.addPredicate(WherePredicate.startsWith(column, prefix));
    }

    public B endsWith(String suffix) {
        Objects.requireNonNull(suffix, "Suffix must not be null");
        return parent.addPredicate(WherePredicate.endsWith(column, suffix));
    }

    public B like(String pattern) {
        Objects.requireNonNull(pattern, "Pattern must not be null");
        return parent.addPredicate(WherePredicate.like(column, pattern));
    }

    public B ilike(String pattern) {
        Objects.requireNonNull(pattern, "Pattern must not be null");
        return parent.addPredicate(WherePredicate.ilike(column, pattern));
    }
}

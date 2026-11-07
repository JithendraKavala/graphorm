package com.graphorm.generator.filter;

import com.graphorm.core.query.WherePredicate;

import java.util.Objects;

/**
 * Filter for string fields providing pattern matching, prefix, suffix, and case-insensitive matching.
 */
public class StringFieldFilter extends ComparableFieldFilter<String> {

    public StringFieldFilter(String column) {
        super(column);
    }

    public WherePredicate contains(String substring) {
        Objects.requireNonNull(substring, "Substring must not be null");
        return WherePredicate.contains(column, substring);
    }

    public WherePredicate startsWith(String prefix) {
        Objects.requireNonNull(prefix, "Prefix must not be null");
        return WherePredicate.startsWith(column, prefix);
    }

    public WherePredicate endsWith(String suffix) {
        Objects.requireNonNull(suffix, "Suffix must not be null");
        return WherePredicate.endsWith(column, suffix);
    }

    public WherePredicate like(String pattern) {
        Objects.requireNonNull(pattern, "Pattern must not be null");
        return WherePredicate.like(column, pattern);
    }

    public WherePredicate ilike(String pattern) {
        Objects.requireNonNull(pattern, "Pattern must not be null");
        return WherePredicate.ilike(column, pattern);
    }
}

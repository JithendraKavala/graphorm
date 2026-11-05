package com.graphorm.core.query;

import java.util.Objects;

/**
 * Strongly typed representation of an SQL HAVING predicate clause.
 *
 * @param predicate the wrapped boolean predicate applied after grouping
 */
public record HavingPredicate(WherePredicate predicate) {

    public HavingPredicate {
        Objects.requireNonNull(predicate, "Having predicate must not be null");
    }

    /**
     * Creates a new {@link HavingPredicate} from a {@link WherePredicate}.
     *
     * @param predicate the predicate
     * @return a new HavingPredicate
     */
    public static HavingPredicate of(WherePredicate predicate) {
        return new HavingPredicate(predicate);
    }

    public static HavingPredicate eq(String field, Object value) {
        return of(WherePredicate.eq(field, value));
    }

    public static HavingPredicate neq(String field, Object value) {
        return of(WherePredicate.neq(field, value));
    }

    public static HavingPredicate ne(String field, Object value) {
        return neq(field, value);
    }

    public static HavingPredicate gt(String field, Object value) {
        return of(WherePredicate.gt(field, value));
    }

    public static HavingPredicate gte(String field, Object value) {
        return of(WherePredicate.gte(field, value));
    }

    public static HavingPredicate lt(String field, Object value) {
        return of(WherePredicate.lt(field, value));
    }

    public static HavingPredicate lte(String field, Object value) {
        return of(WherePredicate.lte(field, value));
    }
}

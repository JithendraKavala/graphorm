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
}

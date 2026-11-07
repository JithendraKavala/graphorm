package com.graphorm.generator.filter;

import com.graphorm.core.query.WherePredicate;

/**
 * Interface implemented by generated filter builder classes to accumulate predicates.
 *
 * @param <B> the concrete filter builder type
 */
public interface FilterBuilder<B extends FilterBuilder<B>> {

    /**
     * Appends a predicate to this builder (combined via logical AND).
     *
     * @param predicate the predicate to append
     * @return this builder
     */
    B addPredicate(WherePredicate predicate);

    /**
     * Builds and returns the combined {@link WherePredicate}.
     *
     * @return the combined predicate, or null if no criteria were added
     */
    WherePredicate build();
}

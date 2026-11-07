package com.graphorm.generator.client;

import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.WherePredicate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Fluent query specification encapsulating filtering predicates, ordering directives,
 * and pagination parameters.
 *
 * @param <F> the filter criteria type
 */
public final class QuerySpec<F> {

    private final Supplier<F> filterSupplier;
    private WherePredicate predicate;
    private final List<OrderByClause> orderBys = new ArrayList<>();
    private Integer limit;
    private Integer offset;

    public QuerySpec(Supplier<F> filterSupplier) {
        this.filterSupplier = Objects.requireNonNull(filterSupplier, "filterSupplier must not be null");
    }

    /**
     * Appends a predicate directly (combined via logical AND).
     *
     * @param predicate the predicate
     * @return this query spec
     */
    public QuerySpec<F> where(WherePredicate predicate) {
        if (predicate != null) {
            this.predicate = this.predicate == null ? predicate : this.predicate.and(predicate);
        }
        return this;
    }

    /**
     * Appends a predicate constructed via a lambda on the filter criteria.
     *
     * @param whereFn filter function
     * @return this query spec
     */
    public QuerySpec<F> where(Function<F, WherePredicate> whereFn) {
        Objects.requireNonNull(whereFn, "whereFn must not be null");
        F filter = filterSupplier.get();
        WherePredicate p = whereFn.apply(filter);
        return where(p);
    }

    /**
     * Appends an ordering criterion.
     *
     * @param orderBy ordering clause
     * @return this query spec
     */
    public QuerySpec<F> orderBy(OrderByClause orderBy) {
        if (orderBy != null) {
            this.orderBys.add(orderBy);
        }
        return this;
    }

    /**
     * Appends an ordering criterion constructed via a lambda on the filter criteria.
     *
     * @param orderByFn ordering function
     * @return this query spec
     */
    public QuerySpec<F> orderBy(Function<F, OrderByClause> orderByFn) {
        Objects.requireNonNull(orderByFn, "orderByFn must not be null");
        F filter = filterSupplier.get();
        OrderByClause ob = orderByFn.apply(filter);
        return orderBy(ob);
    }

    /**
     * Sets the maximum number of records to return.
     *
     * @param limit maximum record count
     * @return this query spec
     */
    public QuerySpec<F> limit(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Limit must not be negative: " + limit);
        }
        this.limit = limit;
        return this;
    }

    /**
     * Sets the pagination offset.
     *
     * @param offset record offset
     * @return this query spec
     */
    public QuerySpec<F> offset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must not be negative: " + offset);
        }
        this.offset = offset;
        return this;
    }

    public WherePredicate getPredicate() {
        return predicate;
    }

    public List<OrderByClause> getOrderBys() {
        return Collections.unmodifiableList(orderBys);
    }

    public Integer getLimit() {
        return limit;
    }

    public Integer getOffset() {
        return offset;
    }
}

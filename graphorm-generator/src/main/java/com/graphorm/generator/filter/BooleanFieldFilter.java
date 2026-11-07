package com.graphorm.generator.filter;

/**
 * Filter for boolean fields.
 */
public class BooleanFieldFilter extends FieldFilter<Boolean> {

    public BooleanFieldFilter(String column) {
        super(column);
    }

    public com.graphorm.core.query.WherePredicate isTrue() {
        return eq(Boolean.TRUE);
    }

    public com.graphorm.core.query.WherePredicate isFalse() {
        return eq(Boolean.FALSE);
    }
}

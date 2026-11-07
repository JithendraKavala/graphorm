package com.graphorm.generator.filter;

/**
 * Filter for 64-bit long integer fields.
 */
public class LongFieldFilter extends ComparableFieldFilter<Long> {

    public LongFieldFilter(String column) {
        super(column);
    }
}

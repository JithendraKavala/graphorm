package com.graphorm.generator.filter;

/**
 * Filter for 32-bit integer fields.
 */
public class IntFieldFilter extends ComparableFieldFilter<Integer> {

    public IntFieldFilter(String column) {
        super(column);
    }
}

package com.graphorm.generator.filter;

/**
 * Filter for double/floating-point fields.
 */
public class DoubleFieldFilter extends ComparableFieldFilter<Double> {

    public DoubleFieldFilter(String column) {
        super(column);
    }
}

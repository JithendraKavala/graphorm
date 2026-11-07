package com.graphorm.generator.filter;

import java.time.Instant;

/**
 * Filter for DateTime / timestamp fields.
 */
public class DateTimeFieldFilter extends ComparableFieldFilter<Instant> {

    public DateTimeFieldFilter(String column) {
        super(column);
    }
}

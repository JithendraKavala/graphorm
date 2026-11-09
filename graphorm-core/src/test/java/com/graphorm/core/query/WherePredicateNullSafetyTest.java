package com.graphorm.core.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WherePredicate Null-Safety and Immutability Tests")
class WherePredicateNullSafetyTest {

    @Test
    @DisplayName("Should allow null parameter values in RowComparisonPredicate without NullPointerException")
    void shouldAllowNullInRowComparison() {
        List<Object> values = new ArrayList<>();
        values.add("cursor-token");
        values.add(null);

        WherePredicate predicate = WherePredicate.rowComparison(
                List.of("token", "created_at"),
                ComparisonOperator.GREATER_THAN,
                values
        );

        assertThat(predicate).isInstanceOf(WherePredicate.RowComparisonPredicate.class);
        WherePredicate.RowComparisonPredicate rowComp = (WherePredicate.RowComparisonPredicate) predicate;
        assertThat(rowComp.values()).containsExactly("cursor-token", null);
    }

    @Test
    @DisplayName("Should allow null parameter values in RawPredicate without NullPointerException")
    void shouldAllowNullInRawPredicate() {
        WherePredicate raw = WherePredicate.raw("status = ? AND deleted_at = ?", "ACTIVE", null);

        assertThat(raw).isInstanceOf(WherePredicate.RawPredicate.class);
        WherePredicate.RawPredicate rp = (WherePredicate.RawPredicate) raw;
        assertThat(rp.parameters()).containsExactly("ACTIVE", null);
    }
}

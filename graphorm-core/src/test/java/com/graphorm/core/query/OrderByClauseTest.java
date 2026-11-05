package com.graphorm.core.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderByClause Unit Tests (TASK-5.3)")
class OrderByClauseTest {

    @Test
    @DisplayName("Should create ASC and DESC ordering criteria with defaults")
    void shouldCreateBasicOrdering() {
        OrderByClause asc = OrderByClause.asc("name");
        assertThat(asc.expression()).isEqualTo("name");
        assertThat(asc.direction()).isEqualTo(OrderDirection.ASC);
        assertThat(asc.nullsOrder()).isEqualTo(NullsOrder.DEFAULT);

        OrderByClause desc = OrderByClause.desc("gpa");
        assertThat(desc.expression()).isEqualTo("gpa");
        assertThat(desc.direction()).isEqualTo(OrderDirection.DESC);
        assertThat(desc.nullsOrder()).isEqualTo(NullsOrder.DEFAULT);
    }

    @Test
    @DisplayName("Should create ordering with explicit nulls positioning")
    void shouldCreateOrderingWithNullsPositioning() {
        OrderByClause ascFirst = OrderByClause.ascNullsFirst("score");
        assertThat(ascFirst.expression()).isEqualTo("score");
        assertThat(ascFirst.direction()).isEqualTo(OrderDirection.ASC);
        assertThat(ascFirst.nullsOrder()).isEqualTo(NullsOrder.FIRST);

        OrderByClause ascLast = OrderByClause.ascNullsLast("score");
        assertThat(ascLast.direction()).isEqualTo(OrderDirection.ASC);
        assertThat(ascLast.nullsOrder()).isEqualTo(NullsOrder.LAST);

        OrderByClause descFirst = OrderByClause.descNullsFirst("created_at");
        assertThat(descFirst.direction()).isEqualTo(OrderDirection.DESC);
        assertThat(descFirst.nullsOrder()).isEqualTo(NullsOrder.FIRST);

        OrderByClause descLast = OrderByClause.descNullsLast("created_at");
        assertThat(descLast.direction()).isEqualTo(OrderDirection.DESC);
        assertThat(descLast.nullsOrder()).isEqualTo(NullsOrder.LAST);

        OrderByClause custom = OrderByClause.of("ranking", OrderDirection.DESC, NullsOrder.FIRST);
        assertThat(custom.expression()).isEqualTo("ranking");
        assertThat(custom.direction()).isEqualTo(OrderDirection.DESC);
        assertThat(custom.nullsOrder()).isEqualTo(NullsOrder.FIRST);
    }

    @Test
    @DisplayName("Should default null direction and null nullsOrder")
    void shouldHandleNullDefaults() {
        OrderByClause clause = new OrderByClause("age", null, null);
        assertThat(clause.direction()).isEqualTo(OrderDirection.ASC);
        assertThat(clause.nullsOrder()).isEqualTo(NullsOrder.DEFAULT);
    }

    @Test
    @DisplayName("Should reject null or blank expression")
    void shouldRejectInvalidExpression() {
        assertThatThrownBy(() -> OrderByClause.asc(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> OrderByClause.asc(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OrderByClause.asc("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

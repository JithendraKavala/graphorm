package com.graphorm.jdbc.builder;

import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParameterLimitChunkingTest {

    private final PostgreSqlDialect dialect = new PostgreSqlDialect();

    @Test
    @DisplayName("IN clause with <= 1000 items is rendered as single IN expression")
    void inClauseUnderLimit() {
        List<Object> items = List.of(1, 2, 3);
        WherePredicate pred = WherePredicate.in("id", items);
        BoundSql bound = WhereClauseBuilder.build(pred, dialect);

        assertThat(bound.sql()).isEqualTo("\"id\" IN (?, ?, ?)");
        assertThat(bound.parameters()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("IN clause with > 1000 items is transparently partitioned into chunks joined by OR")
    void inClausePartitioned() {
        List<Object> items = new ArrayList<>();
        for (int i = 1; i <= 2500; i++) {
            items.add(i);
        }

        WherePredicate pred = WherePredicate.in("id", items);
        BoundSql bound = WhereClauseBuilder.build(pred, dialect);

        String sql = bound.sql();
        assertThat(sql).startsWith("(\"id\" IN (");
        assertThat(sql).contains(") OR \"id\" IN (");
        assertThat(bound.parameters()).hasSize(2500);
        assertThat(bound.parameters().get(0)).isEqualTo(1);
        assertThat(bound.parameters().get(2499)).isEqualTo(2500);
    }

    @Test
    @DisplayName("NOT IN clause with > 1000 items is transparently partitioned into chunks joined by AND")
    void notInClausePartitioned() {
        List<Object> items = new ArrayList<>();
        for (int i = 1; i <= 2500; i++) {
            items.add(i);
        }

        WherePredicate pred = WherePredicate.notIn("id", items);
        BoundSql bound = WhereClauseBuilder.build(pred, dialect);

        String sql = bound.sql();
        assertThat(sql).startsWith("(\"id\" NOT IN (");
        assertThat(sql).contains(") AND \"id\" NOT IN (");
        assertThat(bound.parameters()).hasSize(2500);
    }
}

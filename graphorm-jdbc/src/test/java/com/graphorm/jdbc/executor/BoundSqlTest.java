package com.graphorm.jdbc.executor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BoundSql Unit Tests")
class BoundSqlTest {

    @Test
    @DisplayName("Should permit null parameter bindings without NullPointerException")
    void shouldPermitNullParameters() {
        BoundSql boundSql = BoundSql.of("INSERT INTO users (id, bio) VALUES (?, ?)", 1L, null);

        assertThat(boundSql.sql()).isEqualTo("INSERT INTO users (id, bio) VALUES (?, ?)");
        assertThat(boundSql.parameters()).hasSize(2);
        assertThat(boundSql.parameters().get(0)).isEqualTo(1L);
        assertThat(boundSql.parameters().get(1)).isNull();
    }

    @Test
    @DisplayName("Should accept List with null elements in constructor")
    void shouldAcceptListWithNullsInConstructor() {
        List<Object> params = new ArrayList<>();
        params.add("test");
        params.add(null);
        params.add(42);

        BoundSql boundSql = new BoundSql("SELECT * FROM items WHERE a = ? AND b = ? AND c = ?", params);

        assertThat(boundSql.parameters()).containsExactly("test", null, 42);
        assertThatThrownBy(() -> boundSql.parameters().add("immutable"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

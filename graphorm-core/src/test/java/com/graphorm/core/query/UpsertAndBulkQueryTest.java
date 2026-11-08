package com.graphorm.core.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UpsertQuery and Bulk Operations Unit Tests")
class UpsertAndBulkQueryTest {

    @Test
    @DisplayName("Should build valid UpsertQuery with DO NOTHING")
    void testUpsertDoNothing() {
        UpsertQuery query = UpsertQuery.into("users")
                .value("id", 1L)
                .value("email", "test@example.com")
                .onConflict("email")
                .doNothing()
                .returningAll()
                .build();

        assertThat(query.queryType()).isEqualTo(QueryType.UPSERT);
        assertThat(query.table().tableName()).isEqualTo("users");
        assertThat(query.conflictAction()).isEqualTo(ConflictAction.DO_NOTHING);
        assertThat(query.conflictColumns()).containsExactly("email");
        assertThat(query.values()).containsEntry("id", 1L).containsEntry("email", "test@example.com");
        assertThat(query.hasReturning()).isTrue();
    }

    @Test
    @DisplayName("Should build valid UpsertQuery with DO UPDATE SET")
    void testUpsertDoUpdate() {
        UpsertQuery query = UpsertQuery.into("users")
                .value("id", 1L)
                .value("name", "Alice")
                .onConflict("id")
                .doUpdateSet("name", "Alice Updated")
                .build();

        assertThat(query.conflictAction()).isEqualTo(ConflictAction.DO_UPDATE);
        assertThat(query.updateValues()).containsEntry("name", "Alice Updated");
    }

    @Test
    @DisplayName("Should build valid BatchInsertQuery with multi-row values")
    void testBatchInsert() {
        BatchInsertQuery query = BatchInsertQuery.into("students")
                .columns("name", "age")
                .addRow("Alice", 20)
                .addRow("Bob", 22)
                .returningAll()
                .build();

        assertThat(query.queryType()).isEqualTo(QueryType.BATCH_INSERT);
        assertThat(query.rowCount()).isEqualTo(2);
        assertThat(query.columns()).containsExactly("name", "age");
        assertThat(query.rows().get(0)).containsExactly("Alice", 20);
        assertThat(query.rows().get(1)).containsExactly("Bob", 22);
    }

    @Test
    @DisplayName("Should build BatchInsertQuery fromMaps")
    void testBatchInsertFromMaps() {
        BatchInsertQuery query = BatchInsertQuery.into("departments")
                .fromMaps(java.util.List.of(
                        Map.of("code", "CS", "name", "Computer Science"),
                        Map.of("code", "EE", "name", "Electrical Eng")
                ))
                .build();

        assertThat(query.rowCount()).isEqualTo(2);
        assertThat(query.columns()).containsExactlyInAnyOrder("code", "name");
    }

    @Test
    @DisplayName("Should build valid BulkUpdateQuery")
    void testBulkUpdate() {
        BulkUpdateQuery query = BulkUpdateQuery.table("students")
                .set("status", "ACTIVE")
                .where(WherePredicate.eq("department_id", 1L))
                .build();

        assertThat(query.queryType()).isEqualTo(QueryType.BULK_UPDATE);
        assertThat(query.values()).containsEntry("status", "ACTIVE");
        assertThat(query.where()).isNotNull();
    }

    @Test
    @DisplayName("Should build CommonTableExpression")
    void testCommonTableExpression() {
        SelectQuery subquery = SelectQuery.from("orders").column("id").build();
        CommonTableExpression cte = CommonTableExpression.of("recent_orders", subquery);

        assertThat(cte.name()).isEqualTo("recent_orders");
        assertThat(cte.recursive()).isFalse();
        assertThat(cte.query()).isEqualTo(subquery);

        SelectQuery mainQuery = SelectQuery.from("recent_orders")
                .with(cte)
                .column("*")
                .build();

        assertThat(mainQuery.hasCtes()).isTrue();
        assertThat(mainQuery.ctes()).containsExactly(cte);
    }
}

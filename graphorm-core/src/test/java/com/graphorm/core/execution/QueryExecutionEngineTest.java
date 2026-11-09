package com.graphorm.core.execution;

import com.graphorm.core.query.BatchInsertQuery;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.core.query.WherePredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QueryExecutionEngine SPI Unit Tests")
class QueryExecutionEngineTest {

    static class MockQueryExecutionEngine implements QueryExecutionEngine {
        @Override
        public List<Map<String, Object>> executeSelect(SelectQuery query) {
            if ("empty".equals(query.source().identifier())) {
                return Collections.emptyList();
            }
            return List.of(Map.of("id", 1L, "name", "Alice"));
        }

        @Override
        public int executeInsert(InsertQuery query) {
            return 1;
        }

        @Override
        public Optional<Map<String, Object>> executeInsertAndReturn(InsertQuery query) {
            return Optional.of(Map.of("id", 100L, "name", query.values().get("name")));
        }

        @Override
        public int executeUpdate(UpdateQuery query) {
            return 1;
        }

        @Override
        public Optional<Map<String, Object>> executeUpdateAndReturn(UpdateQuery query) {
            return Optional.of(Map.of("id", 1L, "name", query.values().get("name")));
        }

        @Override
        public int executeDelete(DeleteQuery query) {
            return 1;
        }

        @Override
        public int executeBatchInsert(BatchInsertQuery query) {
            return query.rows().size();
        }

        @Override
        public List<Map<String, Object>> executeBatchInsertAndReturn(BatchInsertQuery query) {
            return query.rows().stream()
                    .map(row -> Map.of("id", 1L, "col", row.get(0)))
                    .toList();
        }
    }

    private final QueryExecutionEngine engine = new MockQueryExecutionEngine();

    @Test
    @DisplayName("executeSelect and executeSelectOne return expected results")
    void testExecuteSelectAndSelectOne() {
        SelectQuery query = SelectQuery.from("users").column("id").column("name").build();
        List<Map<String, Object>> rows = engine.executeSelect(query);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("name", "Alice");

        Optional<Map<String, Object>> single = engine.executeSelectOne(query);
        assertThat(single).isPresent();
        assertThat(single.get()).containsEntry("name", "Alice");

        SelectQuery emptyQuery = SelectQuery.from("empty").column("id").build();
        assertThat(engine.executeSelectOne(emptyQuery)).isEmpty();

        // Convenience aliases
        assertThat(engine.queryForList(query)).hasSize(1);
        assertThat(engine.queryForObject(query)).isPresent();
    }

    @Test
    @DisplayName("executeInsert and executeInsertAndReturn work properly")
    void testExecuteInsert() {
        InsertQuery insert = InsertQuery.into("users").value("name", "Bob").build();
        assertThat(engine.executeInsert(insert)).isEqualTo(1);

        Optional<Map<String, Object>> created = engine.executeInsertAndReturn(insert);
        assertThat(created).isPresent();
        assertThat(created.get()).containsEntry("id", 100L).containsEntry("name", "Bob");
    }

    @Test
    @DisplayName("executeUpdate and executeDelete work properly")
    void testExecuteUpdateAndDelete() {
        UpdateQuery update = UpdateQuery.table("users").set("name", "Charlie").where(WherePredicate.eq("id", 1L)).build();
        assertThat(engine.executeUpdate(update)).isEqualTo(1);
        assertThat(engine.executeUpdateAndReturn(update)).isPresent();

        DeleteQuery delete = DeleteQuery.from("users").where(WherePredicate.eq("id", 1L)).build();
        assertThat(engine.executeDelete(delete)).isEqualTo(1);
        assertThat(engine.executeUpdate(delete)).isEqualTo(1);
    }

    @Test
    @DisplayName("executeBatchInsert works properly")
    void testExecuteBatchInsert() {
        BatchInsertQuery batch = BatchInsertQuery.into("users")
                .columns(List.of("name"))
                .addRow(List.of("Bob"))
                .addRow(List.of("Charlie"))
                .build();
        assertThat(engine.executeBatchInsert(batch)).isEqualTo(2);
        assertThat(engine.executeBatchInsertAndReturn(batch)).hasSize(2);
    }

    @Test
    @DisplayName("Asynchronous methods complete with expected values")
    void testAsyncMethods() throws ExecutionException, InterruptedException {
        SelectQuery select = SelectQuery.from("users").column("id").build();
        CompletableFuture<List<Map<String, Object>>> selectFuture = engine.executeSelectAsync(select);
        assertThat(selectFuture.get()).hasSize(1);

        CompletableFuture<Optional<Map<String, Object>>> singleFuture = engine.executeSelectOneAsync(select);
        assertThat(singleFuture.get()).isPresent();

        InsertQuery insert = InsertQuery.into("users").value("name", "Bob").build();
        assertThat(engine.executeInsertAsync(insert).get()).isEqualTo(1);
        assertThat(engine.executeInsertAndReturnAsync(insert).get()).isPresent();

        UpdateQuery update = UpdateQuery.table("users").set("name", "Bob").build();
        assertThat(engine.executeUpdateAsync(update).get()).isEqualTo(1);
        assertThat(engine.executeUpdateAndReturnAsync(update).get()).isPresent();

        DeleteQuery delete = DeleteQuery.from("users").where(WherePredicate.eq("id", 1L)).build();
        assertThat(engine.executeDeleteAsync(delete).get()).isEqualTo(1);
    }
}

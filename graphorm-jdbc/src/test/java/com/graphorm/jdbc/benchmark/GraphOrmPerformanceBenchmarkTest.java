package com.graphorm.jdbc.benchmark;

import com.graphorm.core.pagination.CursorCodec;
import com.graphorm.core.query.BatchInsertQuery;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.UpsertQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import com.graphorm.jdbc.mapper.DynamicRowMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GraphORM Performance Microbenchmark Suite")
public class GraphOrmPerformanceBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(GraphOrmPerformanceBenchmarkTest.class);

    private static final int WARMUP_ITERATIONS = 5_000;
    private static final int MEASUREMENT_ITERATIONS = 20_000;

    public record BenchmarkResult(
            String benchmarkName,
            int iterations,
            double avgNanos,
            double p95Nanos,
            double p99Nanos,
            double throughputOpsPerSec
    ) {}

    public record StudentRecord(
            Long id,
            String name,
            String email,
            Double gpa,
            Long departmentId
    ) {}

    private final SqlDialect dialect = new PostgreSqlDialect();

    @Test
    @DisplayName("Benchmark 1: Query AST Compilation Overhead (SelectQuery, Keyset, CTE)")
    void benchmarkQueryAstCompilation() {
        String cursor = CursorCodec.encode(List.of("2026-01-01T00:00:00Z", 100L));

        SelectQuery cteQuery = SelectQuery.from("orders")
                .columns("id", "total")
                .where(WherePredicate.gt("total", 50))
                .build();

        SelectQuery complexQuery = SelectQuery.from("students")
                .with("active_orders", cteQuery)
                .columns("id", "name", "email", "gpa")
                .where(WherePredicate.and(
                        WherePredicate.eq("department_id", 5),
                        WherePredicate.gte("gpa", 3.5)
                ))
                .orderBy(OrderByClause.asc("created_at"))
                .after(cursor)
                .first(25)
                .build();

        BenchmarkResult result = runBenchmark("Query AST Compilation (SelectQuery + CTE + Keyset)", () -> {
            BoundSql boundSql = dialect.buildSelectSql(complexQuery);
            if (boundSql.sql().isEmpty()) {
                throw new IllegalStateException("Compilation failed");
            }
        });

        log.info("{}", result);
        assertThat(result.avgNanos()).isLessThan(50_000); // Expect sub-50 microseconds average
    }

    @Test
    @DisplayName("Benchmark 2: Upsert & Batch Insert AST Compilation Overhead")
    void benchmarkUpsertAndBatchInsertCompilation() {
        UpsertQuery upsert = UpsertQuery.into("inventory")
                .value("sku", "SKU-99")
                .value("stock", 150)
                .value("price", 19.99)
                .onConflict("sku")
                .doUpdateSet("stock", 150)
                .returning("sku", "stock")
                .build();

        BatchInsertQuery batchInsert = BatchInsertQuery.into("metrics")
                .columns("metric_name", "val", "source")
                .addRow("cpu", 45.2, "server-1")
                .addRow("memory", 78.1, "server-1")
                .addRow("disk", 55.0, "server-1")
                .addRow("network", 120.4, "server-1")
                .returningAll()
                .build();

        BenchmarkResult upsertResult = runBenchmark("Upsert AST Compilation", () -> {
            BoundSql b = dialect.buildUpsertSql(upsert);
            if (b.sql().isEmpty()) throw new IllegalStateException();
        });

        BenchmarkResult batchResult = runBenchmark("Batch Insert AST Compilation (4 rows)", () -> {
            BoundSql b = dialect.buildBatchInsertSql(batchInsert);
            if (b.sql().isEmpty()) throw new IllegalStateException();
        });

        log.info("{}", upsertResult);
        log.info("{}", batchResult);
        assertThat(upsertResult.avgNanos()).isLessThan(30_000);
        assertThat(batchResult.avgNanos()).isLessThan(30_000);
    }

    @Test
    @DisplayName("Benchmark 3: Row Mapping Overhead (Record mapping)")
    void benchmarkRowMappingOverhead() {
        Map<String, Object> rowMap = Map.of(
                "id", 101L,
                "name", "Alice Montgomery",
                "email", "alice@university.edu",
                "gpa", 3.92,
                "departmentId", 12L
        );

        BenchmarkResult result = runBenchmark("DynamicRowMapper.mapMapToRecord", () -> {
            StudentRecord record = DynamicRowMapper.mapMapToRecord(rowMap, StudentRecord.class);
            if (record.id() == null) throw new IllegalStateException();
        });

        log.info("{}", result);
        assertThat(result.avgNanos()).isLessThan(15_000); // Reflection + record constructor instantiation
    }

    @Test
    @DisplayName("Benchmark 4: DataLoader Batch Hydration & Grouping Overhead")
    void benchmarkDataLoaderBatchHydration() {
        // Simulate 1,000 relation items being grouped and mapped to parent IDs
        List<Map<String, Object>> simulatedChildRows = new ArrayList<>(1_000);
        for (int i = 0; i < 1_000; i++) {
            simulatedChildRows.add(Map.of(
                    "id", (long) i,
                    "student_id", (long) (i % 50), // 50 distinct students
                    "course_name", "Course " + (i % 20),
                    "grade_points", 4.0
            ));
        }

        BenchmarkResult result = runBenchmark("DataLoader 1000-Row Key Grouping", () -> {
            Map<Object, List<Map<String, Object>>> grouped = new HashMap<>();
            for (Map<String, Object> row : simulatedChildRows) {
                Object fk = row.get("student_id");
                grouped.computeIfAbsent(fk, k -> new ArrayList<>()).add(row);
            }
            if (grouped.size() != 50) throw new IllegalStateException();
        });

        log.info("{}", result);
        assertThat(result.avgNanos()).isLessThan(200_000); // Sub-200 microseconds for 1000 items
    }

    private BenchmarkResult runBenchmark(String name, Runnable task) {
        // Warmup phase
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            task.run();
        }

        // Measurement phase
        long[] durations = new long[MEASUREMENT_ITERATIONS];
        long totalNanos = 0;

        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long start = System.nanoTime();
            task.run();
            long elapsed = System.nanoTime() - start;
            durations[i] = elapsed;
            totalNanos += elapsed;
        }

        Arrays.sort(durations);
        double avg = (double) totalNanos / MEASUREMENT_ITERATIONS;
        double p95 = durations[(int) (MEASUREMENT_ITERATIONS * 0.95)];
        double p99 = durations[(int) (MEASUREMENT_ITERATIONS * 0.99)];
        double throughput = (MEASUREMENT_ITERATIONS / (double) totalNanos) * 1_000_000_000L;

        return new BenchmarkResult(name, MEASUREMENT_ITERATIONS, avg, p95, p99, throughput);
    }
}

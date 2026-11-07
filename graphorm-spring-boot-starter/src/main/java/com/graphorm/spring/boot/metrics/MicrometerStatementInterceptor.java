package com.graphorm.spring.boot.metrics;

import com.graphorm.jdbc.util.StatementInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Statement interceptor recording SQL query execution durations via Micrometer (TASK-15.4).
 */
public class MicrometerStatementInterceptor implements StatementInterceptor {

    public static final String METRIC_QUERY_DURATION = "graphorm.query.duration";

    private final MeterRegistry meterRegistry;

    public MicrometerStatementInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "MeterRegistry must not be null");
    }

    @Override
    public void beforeExecute(String sql, List<Object> parameters) {
        // No-op before execution
    }

    @Override
    public void afterExecute(String sql, List<Object> parameters, long durationNanos) {
        String queryType = resolveQueryType(sql);
        Timer.builder(METRIC_QUERY_DURATION)
                .description("GraphORM SQL query execution duration")
                .tag("queryType", queryType)
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private static String resolveQueryType(String sql) {
        if (sql == null || sql.isBlank()) {
            return "UNKNOWN";
        }
        String trimmed = sql.stripLeading().toUpperCase(Locale.ROOT);
        if (trimmed.startsWith("SELECT")) {
            return "SELECT";
        } else if (trimmed.startsWith("INSERT")) {
            return "INSERT";
        } else if (trimmed.startsWith("UPDATE")) {
            return "UPDATE";
        } else if (trimmed.startsWith("DELETE")) {
            return "DELETE";
        }
        return "OTHER";
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}

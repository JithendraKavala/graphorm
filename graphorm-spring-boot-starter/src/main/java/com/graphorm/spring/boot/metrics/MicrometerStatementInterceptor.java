package com.graphorm.spring.boot.metrics;

import com.graphorm.jdbc.observability.SqlQueryFingerprinter;
import com.graphorm.jdbc.util.StatementInterceptor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Statement interceptor recording SQL query execution durations and counts via Micrometer,
 * tagged with deterministic SHA-256 SQL fingerprint, model/table, and operation type.
 */
public class MicrometerStatementInterceptor implements StatementInterceptor {

    public static final String METRIC_QUERY_DURATION = "graphorm.query.duration";
    public static final String METRIC_QUERY_COUNT = "graphorm.query.count";

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
        SqlQueryFingerprinter.FingerprintResult fp = SqlQueryFingerprinter.fingerprint(sql);

        Timer.builder(METRIC_QUERY_DURATION)
                .description("GraphORM SQL query execution duration")
                .tag("operation", fp.operation())
                .tag("queryType", fp.operation())
                .tag("model", fp.targetTable())
                .tag("fingerprint", fp.fingerprint())
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);

        Counter.builder(METRIC_QUERY_COUNT)
                .description("GraphORM SQL query execution count")
                .tag("operation", fp.operation())
                .tag("queryType", fp.operation())
                .tag("model", fp.targetTable())
                .tag("fingerprint", fp.fingerprint())
                .register(meterRegistry)
                .increment();
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}

package com.graphorm.spring.boot.metrics;

import com.graphorm.graphql.loader.DataLoaderMetricsListener;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * DataLoader metrics listener recording relationship batch sizes and load durations
 * into a Micrometer {@link MeterRegistry} (TASK-15.4).
 */
public class MicrometerDataLoaderMetricsListener implements DataLoaderMetricsListener {

    public static final String METRIC_BATCH_SIZE = "graphorm.dataloader.batch.size";
    public static final String METRIC_BATCH_DURATION = "graphorm.dataloader.batch.duration";

    private final MeterRegistry meterRegistry;

    public MicrometerDataLoaderMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "MeterRegistry must not be null");
    }

    @Override
    public void onBatchLoaded(String sourceModel, String relationName, int batchSize, long durationNanos) {
        DistributionSummary.builder(METRIC_BATCH_SIZE)
                .description("GraphORM DataLoader batch size")
                .tag("sourceModel", sourceModel != null ? sourceModel : "UNKNOWN")
                .tag("relation", relationName != null ? relationName : "UNKNOWN")
                .register(meterRegistry)
                .record(batchSize);

        Timer.builder(METRIC_BATCH_DURATION)
                .description("GraphORM DataLoader batch execution duration")
                .tag("sourceModel", sourceModel != null ? sourceModel : "UNKNOWN")
                .tag("relation", relationName != null ? relationName : "UNKNOWN")
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}

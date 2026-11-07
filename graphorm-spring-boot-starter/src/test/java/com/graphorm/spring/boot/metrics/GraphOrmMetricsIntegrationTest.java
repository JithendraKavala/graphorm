package com.graphorm.spring.boot.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TASK-15.4: Micrometer Metrics Integration Tests")
class GraphOrmMetricsIntegrationTest {

    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    @DisplayName("MicrometerStatementInterceptor records query execution duration and operation tag")
    void testStatementInterceptorRecordsTimer() {
        MicrometerStatementInterceptor interceptor = new MicrometerStatementInterceptor(meterRegistry);

        interceptor.beforeExecute("SELECT id, name FROM students WHERE id = ?", List.of(1));
        interceptor.afterExecute("SELECT id, name FROM students WHERE id = ?", List.of(1), 5_000_000L); // 5ms

        Timer timer = meterRegistry.find("graphorm.query.duration")
                .tag("queryType", "SELECT")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("MicrometerDataLoaderMetricsListener records batch sizes and durations")
    void testDataLoaderMetricsListenerRecordsBatch() {
        MicrometerDataLoaderMetricsListener listener = new MicrometerDataLoaderMetricsListener(meterRegistry);

        listener.onBatchLoaded("Department", "students", 25, 10_000_000L);
        listener.onBatchLoaded("Department", "students", 15, 6_000_000L);

        DistributionSummary summary = meterRegistry.find("graphorm.dataloader.batch.size")
                .tag("sourceModel", "Department")
                .tag("relation", "students")
                .summary();

        assertThat(summary).isNotNull();
        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.totalAmount()).isEqualTo(40.0);

        Timer durationTimer = meterRegistry.find("graphorm.dataloader.batch.duration")
                .tag("sourceModel", "Department")
                .tag("relation", "students")
                .timer();
        assertThat(durationTimer).isNotNull();
        assertThat(durationTimer.count()).isEqualTo(2);
    }
}

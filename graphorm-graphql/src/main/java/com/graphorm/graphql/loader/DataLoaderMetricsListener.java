package com.graphorm.graphql.loader;

/**
 * Listener interface invoked whenever a DataLoader batch is executed.
 * Used for recording metrics such as batch sizes and execution duration.
 */
@FunctionalInterface
public interface DataLoaderMetricsListener {

    /**
     * Called when a batch of relationship keys has been loaded.
     *
     * @param sourceModel   the source entity model name
     * @param relationName  the relation name being traversed
     * @param batchSize     number of distinct keys in this batch
     * @param durationNanos execution time in nanoseconds
     */
    void onBatchLoaded(String sourceModel, String relationName, int batchSize, long durationNanos);

    /**
     * Default no-op listener.
     */
    DataLoaderMetricsListener NO_OP = (source, relation, size, duration) -> {};
}

package com.graphorm.core.metadata;

/**
 * Granular exposure control metadata for models and view models in GraphQL APIs.
 * Controls whether root query operations (findMany, findUnique, connections) and/or write mutations
 * (create, update, delete) are exposed.
 *
 * @param queries   whether root queries are exposed in GraphQL
 * @param mutations whether mutation operations are exposed in GraphQL (always false for view models)
 */
public record ExposureMetadata(
        boolean queries,
        boolean mutations
) {

    public static final ExposureMetadata ALL = new ExposureMetadata(true, true);
    public static final ExposureMetadata QUERIES_ONLY = new ExposureMetadata(true, false);
    public static final ExposureMetadata MUTATIONS_ONLY = new ExposureMetadata(false, true);
    public static final ExposureMetadata NONE = new ExposureMetadata(false, false);

    public static ExposureMetadata all() {
        return ALL;
    }

    public static ExposureMetadata queriesOnly() {
        return QUERIES_ONLY;
    }

    public static ExposureMetadata mutationsOnly() {
        return MUTATIONS_ONLY;
    }

    public static ExposureMetadata none() {
        return NONE;
    }

    public static ExposureMetadata of(boolean queries, boolean mutations) {
        if (queries && mutations) return ALL;
        if (queries) return QUERIES_ONLY;
        if (mutations) return MUTATIONS_ONLY;
        return NONE;
    }
}

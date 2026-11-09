package com.graphorm.jdbc.cache;

import java.util.Objects;

/**
 * Cache key identifying a compiled query execution plan in the L1 {@link QueryPlanCache}.
 * Contains structural query identity without leaking any runtime argument or parameter values.
 *
 * @param entityName              the name of the queried model or view (e.g. "Student")
 * @param selectionSetFingerprint sorted representation of requested column projections
 * @param filterShape             canonical structural shape of filters, joins, groupings, and pagination
 * @param dialect                 the name of the active SQL dialect
 */
public record QueryPlanKey(
        String entityName,
        String selectionSetFingerprint,
        String filterShape,
        String dialect
) {

    public QueryPlanKey {
        Objects.requireNonNull(entityName, "entityName must not be null");
        selectionSetFingerprint = (selectionSetFingerprint != null && !selectionSetFingerprint.isBlank())
                ? selectionSetFingerprint : "*";
        filterShape = (filterShape != null && !filterShape.isBlank()) ? filterShape : "ALL";
        Objects.requireNonNull(dialect, "dialect must not be null");
    }

    public static QueryPlanKey of(String entityName, String selectionSetFingerprint, String filterShape, String dialect) {
        return new QueryPlanKey(entityName, selectionSetFingerprint, filterShape, dialect);
    }
}

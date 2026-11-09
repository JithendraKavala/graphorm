package com.graphorm.jdbc.cache;

import com.graphorm.core.query.ProjectedColumn;

import java.util.List;
import java.util.Objects;

/**
 * Pre-compiled query plan cached in the L1 {@link QueryPlanCache}.
 * Contains the parameterized SQL template and column projection mappings.
 *
 * @param sqlTemplate      pre-compiled SQL template with '?' positional placeholders
 * @param projectedColumns column projection and alias mappings
 * @param parameterCount   number of bound positional parameters
 */
public record QueryPlan(
        String sqlTemplate,
        List<ProjectedColumn> projectedColumns,
        int parameterCount
) {

    public QueryPlan {
        Objects.requireNonNull(sqlTemplate, "sqlTemplate must not be null");
        projectedColumns = projectedColumns == null ? List.of() : List.copyOf(projectedColumns);
    }

    public static QueryPlan of(String sqlTemplate, List<ProjectedColumn> projectedColumns, int parameterCount) {
        return new QueryPlan(sqlTemplate, projectedColumns, parameterCount);
    }
}

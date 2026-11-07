package com.graphorm.graphql.security;

import graphql.ErrorType;
import graphql.execution.AbortExecutionException;
import graphql.language.SourceLocation;

import java.io.Serial;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exception thrown when an incoming GraphQL query document exceeds the configured maximum complexity budget.
 */
public class GraphOrmComplexityExceededException extends AbortExecutionException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String ERROR_CODE = "GRAPHQL_COMPLEXITY_LIMIT_EXCEEDED";

    private final int totalComplexity;
    private final int maxComplexity;

    public GraphOrmComplexityExceededException(int totalComplexity, int maxComplexity) {
        super("Maximum query complexity exceeded " + totalComplexity + " > " + maxComplexity);
        this.totalComplexity = totalComplexity;
        this.maxComplexity = maxComplexity;
    }

    public int getTotalComplexity() {
        return totalComplexity;
    }

    public int getMaxComplexity() {
        return maxComplexity;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.ExecutionAborted;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return Collections.emptyList();
    }

    @Override
    public Map<String, Object> getExtensions() {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("errorCode", ERROR_CODE);
        extensions.put("code", ERROR_CODE);
        extensions.put("maxComplexity", maxComplexity);
        extensions.put("totalComplexity", totalComplexity);
        extensions.put("status", 400);
        extensions.put("classification", "BAD_REQUEST");
        return extensions;
    }
}

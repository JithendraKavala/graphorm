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
 * Exception thrown when an incoming GraphQL query document exceeds the configured maximum depth limit.
 */
public class GraphOrmDepthExceededException extends AbortExecutionException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String ERROR_CODE = "GRAPHQL_DEPTH_LIMIT_EXCEEDED";

    private final int totalDepth;
    private final int maxDepth;

    public GraphOrmDepthExceededException(int totalDepth, int maxDepth) {
        super("Maximum query depth exceeded " + totalDepth + " > " + maxDepth);
        this.totalDepth = totalDepth;
        this.maxDepth = maxDepth;
    }

    public int getTotalDepth() {
        return totalDepth;
    }

    public int getMaxDepth() {
        return maxDepth;
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
        extensions.put("maxDepth", maxDepth);
        extensions.put("totalDepth", totalDepth);
        extensions.put("status", 400);
        extensions.put("classification", "BAD_REQUEST");
        return extensions;
    }
}

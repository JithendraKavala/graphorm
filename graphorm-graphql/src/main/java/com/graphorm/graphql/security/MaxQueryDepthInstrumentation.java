package com.graphorm.graphql.security;

import graphql.analysis.QueryDepthInfo;
import graphql.execution.AbortExecutionException;

import java.util.function.Function;

/**
 * GraphQL Java instrumentation rejecting incoming queries exceeding a maximum nesting depth (TASK-15.1).
 */
public class MaxQueryDepthInstrumentation extends graphql.analysis.MaxQueryDepthInstrumentation {

    private final int maxDepth;

    public MaxQueryDepthInstrumentation(int maxDepth) {
        super(maxDepth);
        this.maxDepth = maxDepth;
    }

    public MaxQueryDepthInstrumentation(int maxDepth, Function<QueryDepthInfo, Boolean> maxQueryDepthCallback) {
        super(maxDepth, maxQueryDepthCallback);
        this.maxDepth = maxDepth;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    @Override
    protected AbortExecutionException mkAbortException(int totalDepth, int maxDepth) {
        return new GraphOrmDepthExceededException(totalDepth, maxDepth);
    }
}

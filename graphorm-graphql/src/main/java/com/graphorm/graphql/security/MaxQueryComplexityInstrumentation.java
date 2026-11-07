package com.graphorm.graphql.security;

import graphql.analysis.FieldComplexityCalculator;
import graphql.analysis.QueryComplexityInfo;
import graphql.execution.AbortExecutionException;

import java.util.function.Function;

/**
 * GraphQL Java instrumentation rejecting incoming queries exceeding a maximum complexity score (TASK-15.1).
 */
public class MaxQueryComplexityInstrumentation extends graphql.analysis.MaxQueryComplexityInstrumentation {

    private final int maxComplexity;

    public MaxQueryComplexityInstrumentation(int maxComplexity) {
        this(maxComplexity, new DefaultFieldComplexityCalculator());
    }

    public MaxQueryComplexityInstrumentation(int maxComplexity, FieldComplexityCalculator fieldComplexityCalculator) {
        super(maxComplexity, fieldComplexityCalculator != null ? fieldComplexityCalculator : new DefaultFieldComplexityCalculator());
        this.maxComplexity = maxComplexity;
    }

    public MaxQueryComplexityInstrumentation(
            int maxComplexity,
            FieldComplexityCalculator fieldComplexityCalculator,
            Function<QueryComplexityInfo, Boolean> maxQueryComplexityCallback
    ) {
        super(
                maxComplexity,
                fieldComplexityCalculator != null ? fieldComplexityCalculator : new DefaultFieldComplexityCalculator(),
                maxQueryComplexityCallback
        );
        this.maxComplexity = maxComplexity;
    }

    public int getMaxComplexity() {
        return maxComplexity;
    }

    @Override
    protected AbortExecutionException mkAbortException(int totalComplexity, int maxComplexity) {
        return new GraphOrmComplexityExceededException(totalComplexity, maxComplexity);
    }
}

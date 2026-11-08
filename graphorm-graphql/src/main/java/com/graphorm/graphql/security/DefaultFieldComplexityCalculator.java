package com.graphorm.graphql.security;

import graphql.analysis.FieldComplexityCalculator;
import graphql.analysis.FieldComplexityEnvironment;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLTypeUtil;

/**
 * Default field complexity calculator assigning higher weight to relation and list fields
 * and accounting for pagination arguments (TASK-15.1).
 */
public class DefaultFieldComplexityCalculator implements FieldComplexityCalculator {

    public static final int DEFAULT_SCALAR_COMPLEXITY = 1;
    public static final int DEFAULT_RELATION_COMPLEXITY = 5;

    private final int scalarComplexity;
    private final int relationComplexity;

    public DefaultFieldComplexityCalculator() {
        this(DEFAULT_SCALAR_COMPLEXITY, DEFAULT_RELATION_COMPLEXITY);
    }

    public DefaultFieldComplexityCalculator(int scalarComplexity, int relationComplexity) {
        this.scalarComplexity = Math.max(1, scalarComplexity);
        this.relationComplexity = Math.max(1, relationComplexity);
    }

    @Override
    public int calculate(FieldComplexityEnvironment environment, int childComplexity) {
        if (environment == null || environment.getFieldDefinition() == null) {
            return childComplexity + scalarComplexity;
        }

        GraphQLOutputType type = environment.getFieldDefinition().getType();
        boolean isLeaf = GraphQLTypeUtil.isLeaf(type);
        int baseWeight = isLeaf ? scalarComplexity : relationComplexity;

        // Multiply child complexity if list pagination arguments are provided
        if (environment.getArguments() != null) {
            Object limitArg = environment.getArguments().get("limit");
            if (limitArg == null) {
                limitArg = environment.getArguments().get("first");
            }
            if (limitArg == null) {
                limitArg = environment.getArguments().get("last");
            }
            if (limitArg == null) {
                limitArg = environment.getArguments().get("batchSize");
            }
            if (limitArg instanceof Number num && num.intValue() > 0) {
                return baseWeight + (num.intValue() * Math.max(1, childComplexity));
            }
        }

        return baseWeight + childComplexity;
    }

    public int getScalarComplexity() {
        return scalarComplexity;
    }

    public int getRelationComplexity() {
        return relationComplexity;
    }
}

package com.graphorm.spring.boot.graphql;

import com.graphorm.graphql.security.GraphOrmDataFetcherExceptionHandler;
import graphql.GraphQLError;
import graphql.execution.ResultPath;
import graphql.language.SourceLocation;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;

/**
 * Spring for GraphQL exception resolver sanitizing internal database and framework exceptions
 * into client-safe machine-readable error codes (TASK-15.3).
 */
public class GraphOrmExceptionHandler extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        SourceLocation location = env != null && env.getField() != null
                ? env.getField().getSourceLocation()
                : null;
        ResultPath path = env != null ? env.getExecutionStepInfo().getPath() : null;

        return GraphOrmDataFetcherExceptionHandler.sanitize(ex, location, path);
    }
}

package com.graphorm.graphql.security;

import com.graphorm.core.exception.AccessDeniedException;
import com.graphorm.core.exception.EntityNotFoundException;
import com.graphorm.core.exception.GraphOrmException;
import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.exception.TenantContextMissingException;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import graphql.execution.ResultPath;
import graphql.language.SourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sanitizing DataFetcher exception handler masking raw database errors, table names,
 * and internal stack traces while emitting client-safe machine-readable error codes (TASK-15.3).
 */
public class GraphOrmDataFetcherExceptionHandler implements DataFetcherExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GraphOrmDataFetcherExceptionHandler.class);

    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(DataFetcherExceptionHandlerParameters params) {
        Throwable exception = params.getException();
        SourceLocation location = params.getSourceLocation();
        ResultPath path = params.getPath();

        GraphQLError error = sanitize(exception, location, path);
        return CompletableFuture.completedFuture(
                DataFetcherExceptionHandlerResult.newResult().error(error).build()
        );
    }

    /**
     * Converts any throwable into a client-safe, sanitized {@link GraphQLError}.
     *
     * @param ex       the caught exception
     * @param location source location in the GraphQL document
     * @param path     execution result path
     * @return client-safe GraphQLError
     */
    public static GraphQLError sanitize(Throwable ex, SourceLocation location, ResultPath path) {
        Throwable root = unwrap(ex);

        // Multi-Tenant Context Missing
        if (root instanceof TenantContextMissingException tcme) {
            return buildError(
                    tcme.getMessage(),
                    "TENANT_CONTEXT_MISSING",
                    403,
                    "FORBIDDEN",
                    location,
                    path
            );
        }

        // 1. Access Control / Security
        if (root instanceof AccessDeniedException ade) {
            return buildError(
                    "Access denied: " + ade.getMessage(),
                    "ACCESS_DENIED",
                    403,
                    "FORBIDDEN",
                    location,
                    path
            );
        }

        // 2. Record Not Found
        if (root instanceof EntityNotFoundException enfe) {
            return buildError(
                    enfe.getMessage(),
                    "RECORD_NOT_FOUND",
                    404,
                    "NOT_FOUND",
                    location,
                    path
            );
        }

        // 3. Read-Only View Violation
        if (root instanceof ReadOnlyViewException rove) {
            return buildError(
                    rove.getMessage(),
                    "READ_ONLY_VIEW",
                    400,
                    "BAD_REQUEST",
                    location,
                    path
            );
        }

        // 4. Depth Limit Exceeded
        if (root instanceof GraphOrmDepthExceededException dee) {
            return buildError(
                    dee.getMessage(),
                    GraphOrmDepthExceededException.ERROR_CODE,
                    400,
                    "BAD_REQUEST",
                    location,
                    path
            );
        }

        // 5. Complexity Limit Exceeded
        if (root instanceof GraphOrmComplexityExceededException cee) {
            return buildError(
                    cee.getMessage(),
                    GraphOrmComplexityExceededException.ERROR_CODE,
                    400,
                    "BAD_REQUEST",
                    location,
                    path
            );
        }

        // 6. Invalid Input Argument
        if (root instanceof IllegalArgumentException iae) {
            return buildError(
                    iae.getMessage(),
                    "INVALID_ARGUMENT",
                    400,
                    "BAD_REQUEST",
                    location,
                    path
            );
        }

        // 7. General GraphORM Domain Exception with existing errorCode
        if (root instanceof GraphOrmException goe && goe.hasErrorCode()) {
            return buildError(
                    goe.getMessage(),
                    goe.getErrorCode(),
                    400,
                    "BAD_REQUEST",
                    location,
                    path
            );
        }

        // 8. Raw Database or Internal Server Error (Mask details!)
        log.error("Internal database or runtime error during GraphQL execution at path {}: {}",
                path != null ? path.toString() : "[unknown]", root.getMessage(), root);

        return buildError(
                "An internal database error occurred while processing the request",
                "INTERNAL_ERROR",
                500,
                "INTERNAL_ERROR",
                location,
                path
        );
    }

    private static GraphQLError buildError(
            String message,
            String errorCode,
            int httpStatus,
            String classification,
            SourceLocation location,
            ResultPath path
    ) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("errorCode", errorCode);
        extensions.put("code", errorCode);
        extensions.put("status", httpStatus);
        extensions.put("classification", classification);

        GraphqlErrorBuilder<?> builder = GraphqlErrorBuilder.newError()
                .message(message)
                .errorType(ErrorType.DataFetchingException)
                .extensions(extensions);

        if (location != null) {
            builder.location(location);
        }
        if (path != null) {
            builder.path(path);
        }
        return builder.build();
    }

    private static Throwable unwrap(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}

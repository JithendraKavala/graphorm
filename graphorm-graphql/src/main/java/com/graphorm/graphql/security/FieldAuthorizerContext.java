package com.graphorm.graphql.security;

import graphql.schema.DataFetchingEnvironment;

import java.util.Objects;

/**
 * Context holder resolving the active {@link FieldAuthorizer} from GraphQLContext,
 * ThreadLocal, or fallback defaults.
 */
public final class FieldAuthorizerContext {

    private static final ThreadLocal<FieldAuthorizer> HOLDER = new ThreadLocal<>();
    private static volatile FieldAuthorizer defaultAuthorizer = FieldAuthorizer.PERMIT_ALL;

    private FieldAuthorizerContext() {}

    /**
     * Resolves the active authorizer, inspecting the {@link DataFetchingEnvironment}'s GraphQLContext first,
     * falling back to the current thread-local authorizer, or the global default authorizer.
     *
     * @param env DataFetchingEnvironment
     * @return the resolved FieldAuthorizer
     */
    public static FieldAuthorizer resolve(DataFetchingEnvironment env) {
        if (env != null && env.getGraphQlContext() != null) {
            FieldAuthorizer authorizer = env.getGraphQlContext().get(FieldAuthorizer.class);
            if (authorizer != null) {
                return authorizer;
            }
        }
        FieldAuthorizer threadAuthorizer = HOLDER.get();
        return threadAuthorizer != null ? threadAuthorizer : defaultAuthorizer;
    }

    /**
     * Sets the global default authorizer (used when no GraphQLContext or ThreadLocal authorizer is present).
     *
     * @param authorizer the default authorizer
     */
    public static void setDefaultAuthorizer(FieldAuthorizer authorizer) {
        defaultAuthorizer = authorizer != null ? authorizer : FieldAuthorizer.PERMIT_ALL;
    }

    /**
     * Returns the global default authorizer.
     */
    public static FieldAuthorizer getDefaultAuthorizer() {
        return defaultAuthorizer;
    }

    /**
     * Sets the thread-local authorizer for the current thread.
     */
    public static void set(FieldAuthorizer authorizer) {
        HOLDER.set(authorizer);
    }

    /**
     * Clears the thread-local authorizer.
     */
    public static void clear() {
        HOLDER.remove();
    }
}

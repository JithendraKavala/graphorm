package com.graphorm.graphql.security;

import com.graphorm.core.exception.AccessDeniedException;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * GraphQL {@link SchemaDirectiveWiring} applying access control checks to any field definition
 * annotated with {@code @auth(roles: [...])} (TASK-15.2).
 */
public class AuthDirectiveWiring implements SchemaDirectiveWiring {

    private static final Logger log = LoggerFactory.getLogger(AuthDirectiveWiring.class);
    public static final String DIRECTIVE_NAME = "auth";

    private final FieldAuthorizer defaultAuthorizer;

    public AuthDirectiveWiring() {
        this(null);
    }

    public AuthDirectiveWiring(FieldAuthorizer defaultAuthorizer) {
        this.defaultAuthorizer = defaultAuthorizer;
    }

    @Override
    public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> env) {
        GraphQLFieldDefinition field = env.getElement();
        GraphQLAppliedDirective directive = env.getAppliedDirective(DIRECTIVE_NAME);
        if (directive == null) {
            return field;
        }

        List<String> requiredRoles = extractRoles(directive);
        if (requiredRoles.isEmpty()) {
            return field;
        }

        DataFetcher<?> originalFetcher = env.getFieldDataFetcher();
        DataFetcher<?> authFetcher = environment -> {
            FieldAuthorizer authorizer = defaultAuthorizer != null
                    ? defaultAuthorizer
                    : FieldAuthorizerContext.resolve(environment);

            if (!authorizer.isAuthorized(requiredRoles)) {
                log.warn("Access denied on GraphQL field '{}'. Required roles: {}, user roles: {}",
                        field.getName(), requiredRoles, authorizer.getCurrentRoles());
                throw new AccessDeniedException(
                        "ACCESS_DENIED",
                        "Access denied to field '" + field.getName() + "'. Required roles: " + requiredRoles
                );
            }
            return originalFetcher.get(environment);
        };

        env.setFieldDataFetcher(authFetcher);
        return field;
    }

    private static List<String> extractRoles(GraphQLAppliedDirective directive) {
        GraphQLAppliedDirectiveArgument rolesArg = directive.getArgument("roles");
        if (rolesArg == null || rolesArg.getValue() == null) {
            return Collections.emptyList();
        }

        Object val = rolesArg.getValue();
        if (val instanceof Collection<?> coll) {
            List<String> roles = new ArrayList<>();
            for (Object item : coll) {
                if (item != null) {
                    roles.add(item.toString());
                }
            }
            return roles;
        } else if (val instanceof String s) {
            return List.of(s);
        }
        return Collections.emptyList();
    }
}

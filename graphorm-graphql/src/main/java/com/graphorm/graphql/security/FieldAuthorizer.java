package com.graphorm.graphql.security;

import java.util.Collection;
import java.util.List;

/**
 * Functional interface evaluating whether the current caller is authorized for the given roles (TASK-15.2).
 */
@FunctionalInterface
public interface FieldAuthorizer {

    /**
     * Determines whether the current security context holds at least one of the required roles.
     *
     * @param requiredRoles list of allowed role identifiers
     * @return true if authorized; false otherwise
     */
    boolean isAuthorized(List<String> requiredRoles);

    /**
     * Permissive authorizer allowing access unconditionally.
     */
    FieldAuthorizer PERMIT_ALL = roles -> true;

    /**
     * Restrictive authorizer denying access unconditionally.
     */
    FieldAuthorizer DENY_ALL = roles -> false;

    /**
     * Returns the active roles of the current caller, or an empty collection if unauthenticated.
     */
    default Collection<String> getCurrentRoles() {
        return List.of();
    }
}

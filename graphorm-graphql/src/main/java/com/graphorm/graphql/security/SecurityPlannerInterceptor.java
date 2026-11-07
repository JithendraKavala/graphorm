package com.graphorm.graphql.security;

import com.graphorm.core.exception.AccessDeniedException;
import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.FieldMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

/**
 * Security interceptor inspecting requested fields during query planning to enforce
 * field-level authorization declared via {@code @auth(roles: [...])} (TASK-15.2).
 */
public final class SecurityPlannerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SecurityPlannerInterceptor.class);

    private SecurityPlannerInterceptor() {}

    /**
     * Verifies that the current caller holds sufficient privileges to access all requested fields
     * on the target entity.
     *
     * @param entity          the target model or view model metadata
     * @param requestedFields the field names requested by the client
     * @param authorizer      the active field authorizer
     * @throws AccessDeniedException if an unauthorized field is requested
     */
    public static void verifyFieldAccess(
            EntityMetadata entity,
            Collection<String> requestedFields,
            FieldAuthorizer authorizer
    ) {
        if (entity == null || requestedFields == null || requestedFields.isEmpty()) {
            return;
        }

        FieldAuthorizer auth = authorizer != null ? authorizer : FieldAuthorizer.PERMIT_ALL;

        for (String fieldName : requestedFields) {
            if (entity.hasField(fieldName)) {
                FieldMetadata field = entity.getField(fieldName);
                if (field.hasAuthRoles()) {
                    List<String> required = field.authRoles();
                    if (!auth.isAuthorized(required)) {
                        log.warn("Access denied to field '{}.{}'. Required roles: {}, user roles: {}",
                                entity.name(), field.name(), required, auth.getCurrentRoles());
                        throw new AccessDeniedException(
                                "ACCESS_DENIED",
                                "Access denied to field '" + field.name() + "' on '" + entity.name()
                                        + "'. Required roles: " + required
                        );
                    }
                }
            }
        }
    }
}

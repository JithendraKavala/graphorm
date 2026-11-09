package com.graphorm.graphql.schema;

import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ViewModelMetadata;

import java.util.Objects;

/**
 * Immutable descriptor binding a GraphQL operation field coordinate (e.g. {@code Query.students} or
 * {@code Mutation.createStudent}) to an underlying GraphORM entity and operation kind.
 *
 * @param parentType        the root operation type name (e.g. "Query" or "Mutation")
 * @param fieldName         the GraphQL field name (e.g. "students", "createStudent")
 * @param entity            the matching model or view metadata
 * @param kind              the operation kind
 * @param idArgumentName    the argument name for primary key lookups (e.g. "id", "studentId")
 * @param inputArgumentName the argument name for input payloads (e.g. "input")
 */
public record GraphQlOperationBinding(
        String parentType,
        String fieldName,
        EntityMetadata entity,
        GraphQlOperationKind kind,
        String idArgumentName,
        String inputArgumentName
) {

    public GraphQlOperationBinding {
        Objects.requireNonNull(parentType, "Parent type must not be null");
        Objects.requireNonNull(fieldName, "Field name must not be null");
        Objects.requireNonNull(entity, "Entity metadata must not be null");
        Objects.requireNonNull(kind, "Operation kind must not be null");
        if (idArgumentName == null || idArgumentName.isBlank()) {
            idArgumentName = "id";
        }
        if (inputArgumentName == null || inputArgumentName.isBlank()) {
            inputArgumentName = "input";
        }
    }

    public boolean isQuery() {
        return "Query".equalsIgnoreCase(parentType);
    }

    public boolean isMutation() {
        return "Mutation".equalsIgnoreCase(parentType);
    }

    public boolean isSubscription() {
        return "Subscription".equalsIgnoreCase(parentType);
    }

    public boolean isModel() {
        return entity instanceof ModelMetadata;
    }

    public boolean isView() {
        return entity instanceof ViewModelMetadata;
    }

    public boolean isRejectedViewMutation() {
        return kind == GraphQlOperationKind.VIEW_MUTATION_REJECTED;
    }
}

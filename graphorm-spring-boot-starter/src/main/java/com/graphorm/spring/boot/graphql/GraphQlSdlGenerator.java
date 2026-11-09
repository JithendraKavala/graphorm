package com.graphorm.spring.boot.graphql;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.core.metadata.ViewModelMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility to generate standard GraphQL SDL schema definitions from a {@link ModelRegistry}.
 */
public final class GraphQlSdlGenerator {

    private GraphQlSdlGenerator() {}

    /**
     * Generates a complete GraphQL SDL schema string matching models and views in the registry.
     * View models and read-only physical models are strictly generated as read-only types and query fields (no mutations).
     * Excludes backend-injected fields (@injected) from input types.
     * Annotates operations with @auth(roles: [...]) when model, view, or field specifies authRoles.
     * Generates Relay-spec compliant Connection, Edge, and PageInfo types for cursor pagination.
     * Respects granular exposure controls (@@expose).
     *
     * @param registry the runtime ModelRegistry
     * @return SDL schema string
     */
    public static String generate(ModelRegistry registry) {
        if (registry == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("directive @auth(roles: [String!]!) on FIELD_DEFINITION\n\n");

        List<String> queryFields = new ArrayList<>();
        List<String> mutationFields = new ArrayList<>();
        List<String> subscriptionFields = new ArrayList<>();
        boolean hasConnections = false;

        // 1. Regular Models
        for (ModelMetadata model : registry.getAllModels()) {
            String modelName = model.name();
            sb.append("type ").append(modelName).append(" {\n");
            for (FieldMetadata field : model.fields()) {
                if (field.isRelation()) {
                    continue;
                }
                sb.append("  ").append(field.name()).append(": ")
                        .append(mapScalarType(field));
                appendAuthDirective(sb, field);
                sb.append("\n");
            }
            for (RelationMetadata rel : model.relations()) {
                String targetType = rel.targetModel();
                if (rel.relationType().isToMany()) {
                    sb.append("  ").append(rel.relationName()).append(": [").append(targetType).append("!]!");
                } else {
                    sb.append("  ").append(rel.relationName()).append(": ").append(targetType);
                }
                FieldMetadata relField = model.findField(rel.relationName()).orElse(null);
                if (relField != null) {
                    appendAuthDirective(sb, relField);
                }
                sb.append("\n");
            }
            sb.append("}\n\n");

            String modelAuth = formatAuthDirective(model.authRoles());

            // If model exposes mutations, generate input types and mutations
            if (model.exposeMutations()) {
                // Input types
                sb.append("input ").append(modelName).append("CreateInput {\n");
                for (FieldMetadata field : model.fields()) {
                    if (field.isRelation() || field.isGenerated() || field.isInjected()) {
                        continue;
                    }
                    sb.append("  ").append(field.name()).append(": ")
                            .append(mapInputScalarType(field)).append("\n");
                }
                sb.append("}\n\n");

                sb.append("input ").append(modelName).append("UpdateInput {\n");
                for (FieldMetadata field : model.fields()) {
                    if (field.isRelation() || field.isGenerated() || field.isPrimaryKey() || field.isInjected()) {
                        continue;
                    }
                    sb.append("  ").append(field.name()).append(": ")
                            .append(mapNullableScalarType(field)).append("\n");
                }
                sb.append("}\n\n");

                // Mutations
                mutationFields.add("create" + modelName + "(input: " + modelName + "CreateInput!): " + modelName + "!" + modelAuth);
                mutationFields.add("update" + modelName + "(id: ID!, input: " + modelName + "UpdateInput!): " + modelName + "!" + modelAuth);
                mutationFields.add("delete" + modelName + "(id: ID!): Boolean!" + modelAuth);
            }

            // If model exposes queries, generate queries & Connection/Edge
            if (model.exposeQueries()) {
                sb.append("type ").append(modelName).append("Edge {\n");
                sb.append("  node: ").append(modelName).append("!\n");
                sb.append("  cursor: String!\n");
                sb.append("}\n\n");

                sb.append("type ").append(modelName).append("Connection {\n");
                sb.append("  edges: [").append(modelName).append("Edge!]!\n");
                sb.append("  pageInfo: PageInfo!\n");
                sb.append("  totalCount: Int\n");
                sb.append("}\n\n");

                hasConnections = true;

                String plural = toLowerCamel(modelName) + "s";
                String singular = toLowerCamel(modelName);
                queryFields.add(plural + ": [" + modelName + "!]!" + modelAuth);
                queryFields.add(singular + "(id: ID!): " + modelName + modelAuth);
                queryFields.add(plural + "Connection(first: Int, after: String, last: Int, before: String): " + modelName + "Connection!" + modelAuth);
            }

            if (model.isNotifyEnabled()) {
                String singular = toLowerCamel(modelName);
                if (model.notifyMetadata().supports(com.graphorm.core.event.NotifyEventType.CREATE)) {
                    subscriptionFields.add(singular + "Created: " + modelName + "!" + modelAuth);
                }
                if (model.notifyMetadata().supports(com.graphorm.core.event.NotifyEventType.UPDATE)) {
                    subscriptionFields.add(singular + "Updated(id: ID): " + modelName + "!" + modelAuth);
                }
                if (model.notifyMetadata().supports(com.graphorm.core.event.NotifyEventType.DELETE)) {
                    subscriptionFields.add(singular + "Deleted: ID!" + modelAuth);
                }
            }
        }

        // 2. View Models (Strictly Read-Only!)
        for (ViewModelMetadata view : registry.getAllViewModels()) {
            String viewName = view.name();
            sb.append("type ").append(viewName).append(" {\n");
            for (FieldMetadata field : view.fields()) {
                sb.append("  ").append(field.name()).append(": ")
                        .append(mapScalarType(field));
                appendAuthDirective(sb, field);
                sb.append("\n");
            }
            sb.append("}\n\n");

            // Strictly ONLY Queries, NEVER any Mutations!
            if (view.exposeQueries()) {
                sb.append("type ").append(viewName).append("Edge {\n");
                sb.append("  node: ").append(viewName).append("!\n");
                sb.append("  cursor: String!\n");
                sb.append("}\n\n");

                sb.append("type ").append(viewName).append("Connection {\n");
                sb.append("  edges: [").append(viewName).append("Edge!]!\n");
                sb.append("  pageInfo: PageInfo!\n");
                sb.append("  totalCount: Int\n");
                sb.append("}\n\n");

                hasConnections = true;

                String viewAuth = formatAuthDirective(view.authRoles());
                String plural = toLowerCamel(viewName) + "s";
                String singular = toLowerCamel(viewName);
                queryFields.add(plural + ": [" + viewName + "!]!" + viewAuth);
                queryFields.add(singular + "(id: ID!): " + viewName + viewAuth);
                queryFields.add(plural + "Connection(first: Int, after: String, last: Int, before: String): " + viewName + "Connection!" + viewAuth);
            }
        }

        if (hasConnections) {
            sb.append("type PageInfo {\n");
            sb.append("  hasNextPage: Boolean!\n");
            sb.append("  hasPreviousPage: Boolean!\n");
            sb.append("  startCursor: String\n");
            sb.append("  endCursor: String\n");
            sb.append("}\n\n");
        }

        // Query Type
        if (!queryFields.isEmpty()) {
            sb.append("type Query {\n");
            for (String qf : queryFields) {
                sb.append("  ").append(qf).append("\n");
            }
            sb.append("}\n\n");
        }

        // Mutation Type
        if (!mutationFields.isEmpty()) {
            sb.append("type Mutation {\n");
            for (String mf : mutationFields) {
                sb.append("  ").append(mf).append("\n");
            }
            sb.append("}\n\n");
        }

        // Subscription Type
        if (!subscriptionFields.isEmpty()) {
            sb.append("type Subscription {\n");
            for (String sf : subscriptionFields) {
                sb.append("  ").append(sf).append("\n");
            }
            sb.append("}\n\n");
        }

        return sb.toString();
    }

    private static void appendAuthDirective(StringBuilder sb, FieldMetadata field) {
        if (field != null && field.hasAuthRoles()) {
            sb.append(formatAuthDirective(field.authRoles()));
        }
    }

    private static String formatAuthDirective(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" @auth(roles: [");
        for (int i = 0; i < roles.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("\"").append(roles.get(i)).append("\"");
        }
        sb.append("])");
        return sb.toString();
    }

    private static String mapScalarType(FieldMetadata field) {
        String base = baseScalar(field);
        return field.isNullable() ? base : base + "!";
    }

    private static String mapInputScalarType(FieldMetadata field) {
        String base = baseScalar(field);
        if (field.isPrimaryKey() || field.defaultValue() != null || field.isNullable()) {
            return base;
        }
        return base + "!";
    }

    private static String mapNullableScalarType(FieldMetadata field) {
        return baseScalar(field);
    }

    private static String baseScalar(FieldMetadata field) {
        if (field.isPrimaryKey()) {
            return "ID";
        }
        String typeName = field.typeName();
        if (typeName == null && field.javaType() != null) {
            typeName = field.javaType().getSimpleName();
        }
        if (typeName == null) {
            return "String";
        }

        return switch (typeName) {
            case "Int", "Integer", "Short", "Byte" -> "Int";
            case "BigInt", "Long" -> "Int";
            case "Float", "Double", "BigDecimal" -> "Float";
            case "Boolean", "bool" -> "Boolean";
            case "ID", "UUID" -> "ID";
            default -> "String";
        };
    }

    private static String toLowerCamel(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}

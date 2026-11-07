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
     * View models are strictly generated as read-only types and query fields (no mutations).
     *
     * @param registry the runtime ModelRegistry
     * @return SDL schema string
     */
    public static String generate(ModelRegistry registry) {
        if (registry == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        List<String> queryFields = new ArrayList<>();
        List<String> mutationFields = new ArrayList<>();

        // 1. Regular Models
        for (ModelMetadata model : registry.getAllModels()) {
            String modelName = model.name();
            sb.append("type ").append(modelName).append(" {\n");
            for (FieldMetadata field : model.fields()) {
                if (field.isRelation()) {
                    continue;
                }
                sb.append("  ").append(field.name()).append(": ")
                        .append(mapScalarType(field)).append("\n");
            }
            for (RelationMetadata rel : model.relations()) {
                String targetType = rel.targetModel();
                if (rel.relationType().isToMany()) {
                    sb.append("  ").append(rel.relationName()).append(": [").append(targetType).append("!]!\n");
                } else {
                    sb.append("  ").append(rel.relationName()).append(": ").append(targetType).append("\n");
                }
            }
            sb.append("}\n\n");

            // Input types
            sb.append("input ").append(modelName).append("CreateInput {\n");
            for (FieldMetadata field : model.fields()) {
                if (field.isRelation() || field.isGenerated()) {
                    continue;
                }
                sb.append("  ").append(field.name()).append(": ")
                        .append(mapInputScalarType(field)).append("\n");
            }
            sb.append("}\n\n");

            sb.append("input ").append(modelName).append("UpdateInput {\n");
            for (FieldMetadata field : model.fields()) {
                if (field.isRelation() || field.isGenerated() || field.isPrimaryKey()) {
                    continue;
                }
                sb.append("  ").append(field.name()).append(": ")
                        .append(mapNullableScalarType(field)).append("\n");
            }
            sb.append("}\n\n");

            // Queries
            String plural = toLowerCamel(modelName) + "s";
            String singular = toLowerCamel(modelName);
            queryFields.add(plural + ": [" + modelName + "!]!");
            queryFields.add(singular + "(id: ID!): " + modelName);

            // Mutations
            mutationFields.add("create" + modelName + "(input: " + modelName + "CreateInput!): " + modelName + "!");
            mutationFields.add("update" + modelName + "(id: ID!, input: " + modelName + "UpdateInput!): " + modelName + "!");
            mutationFields.add("delete" + modelName + "(id: ID!): Boolean!");
        }

        // 2. View Models (Strictly Read-Only!)
        for (ViewModelMetadata view : registry.getAllViewModels()) {
            String viewName = view.name();
            sb.append("type ").append(viewName).append(" {\n");
            for (FieldMetadata field : view.fields()) {
                sb.append("  ").append(field.name()).append(": ")
                        .append(mapScalarType(field)).append("\n");
            }
            sb.append("}\n\n");

            // Strictly ONLY Queries, NEVER any Mutations!
            String plural = toLowerCamel(viewName) + "s";
            String singular = toLowerCamel(viewName);
            queryFields.add(plural + ": [" + viewName + "!]!");
            queryFields.add(singular + "(id: ID!): " + viewName);
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

package com.graphorm.generator.util;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

import java.time.Instant;
import java.util.Objects;

/**
 * Resolves schema field definitions and metadata into JavaPoet {@link TypeName}s.
 */
public final class JavaTypeResolver {

    private JavaTypeResolver() {}

    /**
     * Resolves the JavaPoet {@link TypeName} for an entity or view field.
     *
     * @param field        the field metadata
     * @param registry     the model registry for looking up enums and composite types
     * @param modelPackage the target package where generated models and records reside
     * @return the resolved TypeName
     */
    public static TypeName resolveFieldType(FieldMetadata field, ModelRegistry registry, String modelPackage) {
        Objects.requireNonNull(field, "FieldMetadata must not be null");

        // 1. Direct javaType if present
        if (field.javaType() != null) {
            TypeName tn = TypeName.get(field.javaType());
            return tn.isPrimitive() ? tn.box() : tn;
        }

        String typeName = field.typeName();
        if (typeName == null || typeName.isBlank()) {
            return TypeName.get(Object.class);
        }

        return resolveByTypeName(typeName, registry, modelPackage);
    }

    /**
     * Resolves a schema type name string (e.g. "Int", "String", "Role", "Address") to a {@link TypeName}.
     */
    public static TypeName resolveByTypeName(String typeName, ModelRegistry registry, String modelPackage) {
        Objects.requireNonNull(typeName, "TypeName must not be null");

        return switch (typeName) {
            case "Int", "Integer" -> TypeName.get(Integer.class);
            case "BigInt", "Long" -> TypeName.get(Long.class);
            case "String" -> TypeName.get(String.class);
            case "Boolean" -> TypeName.get(Boolean.class);
            case "Float", "Double" -> TypeName.get(Double.class);
            case "DateTime", "Instant" -> ClassName.get(Instant.class);
            case "Json" -> TypeName.get(String.class);
            case "Bytes" -> ArrayTypeName.of(TypeName.BYTE);
            default -> {
                if (registry != null) {
                    if (registry.findEnum(typeName).isPresent()) {
                        yield ClassName.get(modelPackage, typeName);
                    }
                    if (registry.findCompositeType(typeName).isPresent()) {
                        yield ClassName.get(modelPackage, typeName);
                    }
                    if (registry.findModel(typeName).isPresent()) {
                        yield ClassName.get(modelPackage, typeName);
                    }
                    if (registry.findViewModel(typeName).isPresent()) {
                        yield ClassName.get(modelPackage, typeName);
                    }
                }
                yield ClassName.get(modelPackage, typeName);
            }
        };
    }

    /**
     * Resolves the filter type for a field (e.g. IntFieldFilter, StringFieldFilter).
     */
    public static TypeName resolveFilterFieldType(FieldMetadata field, ModelRegistry registry, String modelPackage) {
        if (field.javaType() != null) {
            Class<?> jt = field.javaType();
            if (Integer.class.isAssignableFrom(jt) || int.class.isAssignableFrom(jt)) {
                return ClassName.get("com.graphorm.generator.filter", "IntFieldFilter");
            } else if (Long.class.isAssignableFrom(jt) || long.class.isAssignableFrom(jt)) {
                return ClassName.get("com.graphorm.generator.filter", "LongFieldFilter");
            } else if (String.class.isAssignableFrom(jt)) {
                return ClassName.get("com.graphorm.generator.filter", "StringFieldFilter");
            } else if (Double.class.isAssignableFrom(jt) || double.class.isAssignableFrom(jt) || Float.class.isAssignableFrom(jt) || float.class.isAssignableFrom(jt)) {
                return ClassName.get("com.graphorm.generator.filter", "DoubleFieldFilter");
            } else if (Boolean.class.isAssignableFrom(jt) || boolean.class.isAssignableFrom(jt)) {
                return ClassName.get("com.graphorm.generator.filter", "BooleanFieldFilter");
            } else if (Instant.class.isAssignableFrom(jt)) {
                return ClassName.get("com.graphorm.generator.filter", "DateTimeFieldFilter");
            }
        }

        String typeName = field.typeName();
        if (typeName != null) {
            switch (typeName) {
                case "Int":
                case "Integer":
                    return ClassName.get("com.graphorm.generator.filter", "IntFieldFilter");
                case "BigInt":
                case "Long":
                    return ClassName.get("com.graphorm.generator.filter", "LongFieldFilter");
                case "String":
                    return ClassName.get("com.graphorm.generator.filter", "StringFieldFilter");
                case "Float":
                case "Double":
                    return ClassName.get("com.graphorm.generator.filter", "DoubleFieldFilter");
                case "Boolean":
                    return ClassName.get("com.graphorm.generator.filter", "BooleanFieldFilter");
                case "DateTime":
                case "Instant":
                    return ClassName.get("com.graphorm.generator.filter", "DateTimeFieldFilter");
                default:
                    break;
            }
        }

        TypeName valueType = resolveFieldType(field, registry, modelPackage);
        return ParameterizedTypeName.get(ClassName.get("com.graphorm.generator.filter", "FieldFilter"), valueType);
    }
}

package com.graphorm.generator.filter;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.generator.engine.GeneratedSourceFile;
import com.graphorm.generator.engine.GeneratorConfig;
import com.graphorm.generator.util.JavaTypeResolver;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.Objects;

/**
 * Generates fluent, type-safe filter criteria classes and filter builders for both
 * regular models and view models.
 */
public final class FilterCriteriaGenerator {

    private FilterCriteriaGenerator() {}

    /**
     * Generates a filter criteria class for a regular model.
     */
    public static GeneratedSourceFile generate(ModelMetadata model, ModelRegistry registry, GeneratorConfig config) {
        Objects.requireNonNull(model, "ModelMetadata must not be null");
        Objects.requireNonNull(config, "GeneratorConfig must not be null");

        List<FieldMetadata> fields = model.fields().stream()
                .filter(f -> !f.isRelation())
                .toList();

        return generateFilter(model.name(), fields, false, registry, config);
    }

    /**
     * Generates a filter criteria class for a view model.
     */
    public static GeneratedSourceFile generate(ViewModelMetadata viewModel, ModelRegistry registry, GeneratorConfig config) {
        Objects.requireNonNull(viewModel, "ViewModelMetadata must not be null");
        Objects.requireNonNull(config, "GeneratorConfig must not be null");

        return generateFilter(viewModel.name(), viewModel.fields(), true, registry, config);
    }

    private static GeneratedSourceFile generateFilter(
            String entityName,
            List<FieldMetadata> fields,
            boolean isView,
            ModelRegistry registry,
            GeneratorConfig config
    ) {
        String packageName = config.filterPackage();
        String className = entityName + "Filter";
        ClassName filterClassName = ClassName.get(packageName, className);
        ClassName builderClassName = filterClassName.nestedClass("Builder");

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Type-safe criteria and filter builder for {@code $L}.\n", entityName);

        // Static singleton INSTANCE
        classBuilder.addField(FieldSpec.builder(filterClassName, "INSTANCE", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T()", filterClassName)
                .build());

        // Static create() and builder()
        classBuilder.addMethod(MethodSpec.methodBuilder("create")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(filterClassName)
                .addStatement("return INSTANCE")
                .build());

        classBuilder.addMethod(MethodSpec.methodBuilder("builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(builderClassName)
                .addStatement("return new $T()", builderClassName)
                .build());

        // Field filter accessor methods on Filter class (e.g. s.name(), s.departmentId())
        for (FieldMetadata field : fields) {
            TypeName filterType = JavaTypeResolver.resolveFilterFieldType(field, registry, config.modelPackage());
            String fieldName = field.name();
            String columnExpression = isView ? field.name() : (field.dbColumnName() != null ? field.dbColumnName() : field.name());

            classBuilder.addMethod(MethodSpec.methodBuilder(fieldName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(filterType)
                    .addJavadoc("Criteria filter for field {@code $L} (column {@code $L}).\n", fieldName, columnExpression)
                    .addStatement("return new $T($S)", filterType, columnExpression)
                    .build());
        }

        // Nested Builder Class implementing FilterBuilder<Builder>
        TypeName filterBuilderInterface = ParameterizedTypeName.get(
                ClassName.get("com.graphorm.generator.filter", "FilterBuilder"),
                builderClassName
        );

        TypeSpec.Builder builderClass = TypeSpec.classBuilder("Builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addSuperinterface(filterBuilderInterface)
                .addJavadoc("Fluent criteria builder for {@link $L}.\n", className);

        builderClass.addField(FieldSpec.builder(WherePredicate.class, "predicate", Modifier.PRIVATE).build());

        // addPredicate(WherePredicate)
        builderClass.addMethod(MethodSpec.methodBuilder("addPredicate")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(builderClassName)
                .addParameter(WherePredicate.class, "predicate")
                .addStatement("if (predicate != null) this.predicate = this.predicate == null ? predicate : this.predicate.and(predicate)")
                .addStatement("return this")
                .build());

        // build()
        builderClass.addMethod(MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(WherePredicate.class)
                .addStatement("return this.predicate")
                .build());

        // Builder criteria methods for each field (e.g. builder.departmentId().eq(10))
        for (FieldMetadata field : fields) {
            String fieldName = field.name();
            String columnExpression = isView ? field.name() : (field.dbColumnName() != null ? field.dbColumnName() : field.name());
            TypeName criteriaBuilderType = resolveCriteriaBuilderType(field, builderClassName, registry, config.modelPackage());

            builderClass.addMethod(MethodSpec.methodBuilder(fieldName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(criteriaBuilderType)
                    .addStatement("return new $T(this, $S)", criteriaBuilderType, columnExpression)
                    .build());
        }

        classBuilder.addType(builderClass.build());

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();

        return new GeneratedSourceFile(packageName, className, javaFile.toString());
    }

    private static TypeName resolveCriteriaBuilderType(
            FieldMetadata field,
            ClassName builderClassName,
            ModelRegistry registry,
            String modelPackage
    ) {
        String typeName = field.typeName();
        if (typeName == null && field.javaType() != null) {
            typeName = field.javaType().getSimpleName();
        }

        if (typeName != null) {
            switch (typeName) {
                case "String":
                    return ParameterizedTypeName.get(
                            ClassName.get("com.graphorm.generator.filter", "StringCriteriaBuilder"),
                            builderClassName
                    );
                case "Int":
                case "Integer":
                    return ParameterizedTypeName.get(
                            ClassName.get("com.graphorm.generator.filter", "ComparableCriteriaBuilder"),
                            builderClassName,
                            ClassName.get(Integer.class)
                    );
                case "BigInt":
                case "Long":
                    return ParameterizedTypeName.get(
                            ClassName.get("com.graphorm.generator.filter", "ComparableCriteriaBuilder"),
                            builderClassName,
                            ClassName.get(Long.class)
                    );
                case "Float":
                case "Double":
                    return ParameterizedTypeName.get(
                            ClassName.get("com.graphorm.generator.filter", "ComparableCriteriaBuilder"),
                            builderClassName,
                            ClassName.get(Double.class)
                    );
                case "DateTime":
                case "Instant":
                    return ParameterizedTypeName.get(
                            ClassName.get("com.graphorm.generator.filter", "ComparableCriteriaBuilder"),
                            builderClassName,
                            ClassName.get(java.time.Instant.class)
                    );
                case "Boolean":
                    return ParameterizedTypeName.get(
                            ClassName.get("com.graphorm.generator.filter", "CriteriaBuilder"),
                            builderClassName,
                            ClassName.get(Boolean.class)
                    );
                default:
                    break;
            }
        }

        TypeName valueType = JavaTypeResolver.resolveFieldType(field, registry, modelPackage);
        return ParameterizedTypeName.get(
                ClassName.get("com.graphorm.generator.filter", "CriteriaBuilder"),
                builderClassName,
                valueType
        );
    }
}

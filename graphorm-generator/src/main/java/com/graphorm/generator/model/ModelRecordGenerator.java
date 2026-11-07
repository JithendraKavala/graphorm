package com.graphorm.generator.model;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.generator.engine.GeneratedSourceFile;
import com.graphorm.generator.engine.GeneratorConfig;
import com.graphorm.generator.util.JavaTypeResolver;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generates immutable Java 21 model records matching schema models with component accessors,
 * with-methods, fluent builder, and table metadata constants.
 */
public final class ModelRecordGenerator {

    private ModelRecordGenerator() {}

    /**
     * Generates an immutable Java 21 record source file for the given {@link ModelMetadata}.
     *
     * @param model    the model metadata
     * @param registry the model registry
     * @param config   the generator configuration
     * @return the generated source file
     */
    public static GeneratedSourceFile generate(ModelMetadata model, ModelRegistry registry, GeneratorConfig config) {
        Objects.requireNonNull(model, "ModelMetadata must not be null");
        Objects.requireNonNull(config, "GeneratorConfig must not be null");

        String packageName = config.modelPackage();
        String modelName = model.name();
        ClassName modelClassName = ClassName.get(packageName, modelName);

        // Persistent (non-relation) fields form the record components
        List<FieldMetadata> persistentFields = model.fields().stream()
                .filter(f -> !f.isRelation())
                .toList();

        // 1. Static Metadata Constants
        FieldSpec modelNameField = FieldSpec.builder(String.class, "MODEL_NAME", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", modelName)
                .build();
        FieldSpec tableNameField = FieldSpec.builder(String.class, "TABLE_NAME", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", model.tableName())
                .build();

        // 2. ClassSpec Builder (JavaPoet generates the body, we format the record header)
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(modelName)
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Immutable Java 21 model record representing the {@code $L} entity (table {@code $L}).\n", modelName, model.tableName())
                .addField(modelNameField)
                .addField(tableNameField);

        // 3. Static builder() factory
        if (config.generateBuilders()) {
            ClassName builderClassName = modelClassName.nestedClass("Builder");
            classBuilder.addMethod(MethodSpec.methodBuilder("builder")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(builderClassName)
                    .addJavadoc("Creates a new {@link Builder} for {@link $L}.\n", modelName)
                    .addStatement("return new $T()", builderClassName)
                    .build());
        }

        // 4. With-methods (wither methods)
        if (config.generateWithMethods()) {
            for (FieldMetadata field : persistentFields) {
                TypeName fieldType = JavaTypeResolver.resolveFieldType(field, registry, packageName);
                String fieldName = field.name();
                String capitalized = capitalize(fieldName);

                MethodSpec.Builder withMethod = MethodSpec.methodBuilder("with" + capitalized)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(modelClassName)
                        .addParameter(fieldType, fieldName)
                        .addJavadoc("Returns a copy of this {@link $L} with the {@code $L} component updated.\n", modelName, fieldName);

                // Build new ModelName(args...)
                StringBuilder callArgs = new StringBuilder("return new $T(");
                List<Object> formatArgs = new ArrayList<>();
                formatArgs.add(modelClassName);

                for (int i = 0; i < persistentFields.size(); i++) {
                    FieldMetadata other = persistentFields.get(i);
                    if (i > 0) callArgs.append(", ");
                    if (other.name().equals(fieldName)) {
                        callArgs.append(fieldName);
                    } else {
                        callArgs.append("this.").append(other.name());
                    }
                }
                callArgs.append(")");
                withMethod.addStatement(callArgs.toString(), formatArgs.toArray());
                classBuilder.addMethod(withMethod.build());
            }
        }

        // 5. Nested Builder Class
        if (config.generateBuilders()) {
            TypeSpec.Builder builderClass = TypeSpec.classBuilder("Builder")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .addJavadoc("Fluent builder for constructing {@link $L} instances.\n", modelName);

            ClassName builderClassName = ClassName.get("", "Builder");

            for (FieldMetadata field : persistentFields) {
                TypeName fieldType = JavaTypeResolver.resolveFieldType(field, registry, packageName);
                String fieldName = field.name();

                builderClass.addField(FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE).build());

                builderClass.addMethod(MethodSpec.methodBuilder(fieldName)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(builderClassName)
                        .addParameter(fieldType, fieldName)
                        .addStatement("this.$L = $L", fieldName, fieldName)
                        .addStatement("return this")
                        .build());
            }

            // build() method
            MethodSpec.Builder buildMethod = MethodSpec.methodBuilder("build")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(modelClassName);

            StringBuilder buildCall = new StringBuilder("return new $T(");
            List<Object> buildArgs = new ArrayList<>();
            buildArgs.add(modelClassName);

            for (int i = 0; i < persistentFields.size(); i++) {
                if (i > 0) buildCall.append(", ");
                buildCall.append(persistentFields.get(i).name());
            }
            buildCall.append(")");
            buildMethod.addStatement(buildCall.toString(), buildArgs.toArray());
            builderClass.addMethod(buildMethod.build());

            classBuilder.addType(builderClass.build());
        }

        // Generate Java code via JavaPoet
        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();

        String rawCode = javaFile.toString();

        // Convert "public class ModelName {" into record signature
        StringBuilder recordHeader = new StringBuilder();
        recordHeader.append("public record ").append(modelName).append("(\n");
        for (int i = 0; i < persistentFields.size(); i++) {
            FieldMetadata f = persistentFields.get(i);
            TypeName ft = JavaTypeResolver.resolveFieldType(f, registry, packageName);
            recordHeader.append("        ").append(ft.toString()).append(" ").append(f.name());
            if (i < persistentFields.size() - 1) {
                recordHeader.append(",");
            }
            recordHeader.append("\n");
        }
        recordHeader.append(") {");

        String recordCode = rawCode.replaceFirst("public class " + modelName + " \\{", recordHeader.toString());

        return new GeneratedSourceFile(packageName, modelName, recordCode);
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}

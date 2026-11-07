package com.graphorm.generator.metadata;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.generator.engine.GeneratedSourceFile;
import com.graphorm.generator.engine.GeneratorConfig;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.Objects;

/**
 * Generates static metadata descriptor classes for models and view models
 * to provide compile-time constants and eliminate runtime reflection.
 */
public final class MetadataGenerator {

    private MetadataGenerator() {}

    public static GeneratedSourceFile generate(ModelMetadata model, ModelRegistry registry, GeneratorConfig config) {
        Objects.requireNonNull(model, "ModelMetadata must not be null");
        Objects.requireNonNull(config, "GeneratorConfig must not be null");

        String packageName = config.metadataPackage();
        String className = model.name() + "Metadata";

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addJavadoc("Static metadata descriptor for {@link $L}.\n", model.name());

        classBuilder.addField(FieldSpec.builder(String.class, "MODEL_NAME", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", model.name())
                .build());

        classBuilder.addField(FieldSpec.builder(String.class, "TABLE_NAME", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", model.tableName())
                .build());

        // Private constructor
        classBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .build());

        // Field name constants
        for (FieldMetadata field : model.fields()) {
            if (!field.isRelation()) {
                String constName = toConstantName(field.name());
                String col = field.dbColumnName() != null ? field.dbColumnName() : field.name();
                classBuilder.addField(FieldSpec.builder(String.class, "FIELD_" + constName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$S", field.name())
                        .build());
                classBuilder.addField(FieldSpec.builder(String.class, "COLUMN_" + constName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$S", col)
                        .build());
            }
        }

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();

        return new GeneratedSourceFile(packageName, className, javaFile.toString());
    }

    public static GeneratedSourceFile generate(ViewModelMetadata viewModel, ModelRegistry registry, GeneratorConfig config) {
        Objects.requireNonNull(viewModel, "ViewModelMetadata must not be null");
        Objects.requireNonNull(config, "GeneratorConfig must not be null");

        String packageName = config.metadataPackage();
        String className = viewModel.name() + "Metadata";

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addJavadoc("Static metadata descriptor for view model {@link $L}.\n", viewModel.name());

        classBuilder.addField(FieldSpec.builder(String.class, "VIEW_NAME", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", viewModel.name())
                .build());

        classBuilder.addField(FieldSpec.builder(boolean.class, "IS_DATABASE_BACKED", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$L", viewModel.isDatabaseBacked())
                .build());

        classBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .build());

        for (FieldMetadata field : viewModel.fields()) {
            String constName = toConstantName(field.name());
            classBuilder.addField(FieldSpec.builder(String.class, "FIELD_" + constName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$S", field.name())
                    .build());
        }

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();

        return new GeneratedSourceFile(packageName, className, javaFile.toString());
    }

    private static String toConstantName(String camelCase) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }
}

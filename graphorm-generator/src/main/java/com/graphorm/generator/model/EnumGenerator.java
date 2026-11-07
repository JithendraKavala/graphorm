package com.graphorm.generator.model;

import com.graphorm.core.metadata.EnumMetadata;
import com.graphorm.generator.engine.GeneratedSourceFile;
import com.graphorm.generator.engine.GeneratorConfig;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.Objects;

/**
 * Generates standard Java enums for schema enums.
 */
public final class EnumGenerator {

    private EnumGenerator() {}

    public static GeneratedSourceFile generate(EnumMetadata enumMetadata, GeneratorConfig config) {
        Objects.requireNonNull(enumMetadata, "EnumMetadata must not be null");
        Objects.requireNonNull(config, "GeneratorConfig must not be null");

        String packageName = config.modelPackage();
        String enumName = enumMetadata.enumName();

        TypeSpec.Builder enumBuilder = TypeSpec.enumBuilder(enumName)
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Enum representing {@code $L}.\n", enumName);

        for (String value : enumMetadata.values()) {
            enumBuilder.addEnumConstant(value);
        }

        JavaFile javaFile = JavaFile.builder(packageName, enumBuilder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();

        return new GeneratedSourceFile(packageName, enumName, javaFile.toString());
    }
}

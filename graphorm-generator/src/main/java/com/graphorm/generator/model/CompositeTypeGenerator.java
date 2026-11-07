package com.graphorm.generator.model;

import com.graphorm.core.metadata.CompositeTypeMetadata;
import com.graphorm.core.metadata.FieldMetadata;
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
 * Generates immutable Java 21 record classes for composite types (embedded value objects).
 */
public final class CompositeTypeGenerator {

    private CompositeTypeGenerator() {}

    public static GeneratedSourceFile generate(CompositeTypeMetadata compositeType, ModelRegistry registry, GeneratorConfig config) {
        Objects.requireNonNull(compositeType, "CompositeTypeMetadata must not be null");
        Objects.requireNonNull(config, "GeneratorConfig must not be null");

        String packageName = config.modelPackage();
        String typeName = compositeType.name();
        ClassName className = ClassName.get(packageName, typeName);

        List<FieldMetadata> fields = compositeType.fields();

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(typeName)
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Immutable composite value object representing {@code $L}.\n", typeName);

        // Builder
        if (config.generateBuilders()) {
            ClassName builderClassName = className.nestedClass("Builder");
            classBuilder.addMethod(MethodSpec.methodBuilder("builder")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(builderClassName)
                    .addStatement("return new $T()", builderClassName)
                    .build());

            TypeSpec.Builder builderClass = TypeSpec.classBuilder("Builder")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);

            for (FieldMetadata field : fields) {
                TypeName ft = JavaTypeResolver.resolveFieldType(field, registry, packageName);
                String fn = field.name();

                builderClass.addField(FieldSpec.builder(ft, fn, Modifier.PRIVATE).build());
                builderClass.addMethod(MethodSpec.methodBuilder(fn)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(ClassName.get("", "Builder"))
                        .addParameter(ft, fn)
                        .addStatement("this.$L = $L", fn, fn)
                        .addStatement("return this")
                        .build());
            }

            MethodSpec.Builder buildMethod = MethodSpec.methodBuilder("build")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(className);

            StringBuilder buildCall = new StringBuilder("return new $T(");
            List<Object> buildArgs = new ArrayList<>();
            buildArgs.add(className);
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) buildCall.append(", ");
                buildCall.append(fields.get(i).name());
            }
            buildCall.append(")");
            buildMethod.addStatement(buildCall.toString(), buildArgs.toArray());
            builderClass.addMethod(buildMethod.build());

            classBuilder.addType(builderClass.build());
        }

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();

        String rawCode = javaFile.toString();

        StringBuilder recordHeader = new StringBuilder();
        recordHeader.append("public record ").append(typeName).append("(\n");
        for (int i = 0; i < fields.size(); i++) {
            FieldMetadata f = fields.get(i);
            TypeName ft = JavaTypeResolver.resolveFieldType(f, registry, packageName);
            recordHeader.append("        ").append(ft.toString()).append(" ").append(f.name());
            if (i < fields.size() - 1) {
                recordHeader.append(",");
            }
            recordHeader.append("\n");
        }
        recordHeader.append(") {");

        String recordCode = rawCode.replaceFirst("public class " + typeName + " \\{", recordHeader.toString());

        return new GeneratedSourceFile(packageName, typeName, recordCode);
    }
}
